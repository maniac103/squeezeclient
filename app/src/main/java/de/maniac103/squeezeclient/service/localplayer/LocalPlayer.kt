/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2025 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */

package de.maniac103.squeezeclient.service.localplayer

import android.content.Context
import android.media.AudioTimestamp
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.extfuncs.LocalPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.localPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.prefs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import okhttp3.Response

class LocalPlayer(
    context: Context,
    private val onPlaybackReady: (buffering: Boolean) -> Unit = {},
    private val onPauseStateChanged: (paused: Boolean) -> Unit = {},
    private val onPlaybackEnded: (streamEnded: Boolean) -> Unit = {},
    private val onPlaybackError: () -> Unit = {},
    private val onDecoderLoadFinished: () -> Unit = {},
    onAudioStreamFlushed: () -> Unit = {},
    private val onHeadersReceived: (response: Response) -> Unit = {},
    private val onMetadataReceived: (title: CharSequence, artworkUri: Uri?) -> Unit = { _, _ -> }
) : Player.Listener {
    private val prefs = context.prefs
    private val dataSourceFactory: HttpDataSource.Factory
    private val player: ExoPlayer
    private var lastPlaybackState = Player.STATE_IDLE

    @UnstableApi
    private lateinit var transferListener: NetworkTransferListener

    var paused: Boolean
        get() = !player.playWhenReady
        set(value) {
            player.playWhenReady = !value
        }

    val playingTitle get() = player.mediaMetadata.title

    val totalTransferredBytes
        @OptIn(UnstableApi::class)
        get() = transferListener.totalBytesTransferred

    val readyForPlaybackOrBuffering get() =
        player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING
    val readyForPlayback get() = player.playbackState == Player.STATE_READY
    val isPlaying get() = player.playbackState == Player.STATE_READY && player.playWhenReady

    var volume: Float
        get() = lastSetVolume ?: 0F
        set(value) {
            lastSetVolume = value
            updatePlayerVolume(true)
        }

    private var lastSetVolume: Float? = null
    private var playerInternalVolume = 1F
    private var currentReplayGain = 1F
    private var lastSavedDeviceVolume: Int? = null

    @UnstableApi
    private val audioProcessor = LocalPlayerAudioProcessor(onAudioStreamFlushed)
    private val audioOutputProvider = LocalPlayerAudioOutputProvider(
        AudioTrackAudioOutputProvider.Builder(context).build()
    )
    private val playbackPositionTimestamp = AudioTimestamp()

    init {
        dataSourceFactory = initDataSourceFactory(context)
        player = initPlayer(context)
    }

    @OptIn(UnstableApi::class)
    private fun initDataSourceFactory(context: Context): HttpDataSource.Factory {
        val client = context.httpClient.newBuilder()
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                onHeadersReceived(response)
                response
            }
            .build()

        transferListener = NetworkTransferListener()
        return OkHttpDataSource.Factory(client)
            .setTransferListener(transferListener)
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer(context: Context): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setRenderersFactory(AudioSinkOverridingFactory(context))
            .setDeviceVolumeControlEnabled(true)
            .build()
        player.addListener(this)
        if (BuildConfig.DEBUG) {
            player.addAnalyticsListener(EventLogger())
        }
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
                onDecoderLoadFinished()
            }
        })
        return player
    }

    @OptIn(UnstableApi::class)
    fun start(
        uri: Uri,
        mimeType: String?,
        headers: Map<String, String>,
        replayGain: Float,
        autoStart: Boolean
    ) {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .build()
        val dataSourceFactoryForHeaders = DataSource.Factory {
            val dataSource = dataSourceFactory.createDataSource()
            headers.forEach { (k, v) -> dataSource.setRequestProperty(k, v) }
            dataSource
        }
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactoryForHeaders)
            .createMediaSource(mediaItem)

        currentReplayGain = replayGain

        player.stop()
        player.setMediaSource(mediaSource)
        player.volume = playerInternalVolume * replayGain
        player.prepare()
        player.playWhenReady = autoStart
    }

    fun stop() {
        player.stop()
    }

    @OptIn(UnstableApi::class)
    fun skipAhead(duration: Duration) {
        audioProcessor.skipAhead(duration)
    }

    @OptIn(UnstableApi::class)
    fun determinePlaybackPosition(nowNanos: Long): Duration {
        val track = audioOutputProvider
            .latestAudioTrack
            ?.takeIf { readyForPlaybackOrBuffering && audioProcessor.hasProcessedData }
        if (track?.getTimestamp(playbackPositionTimestamp) != true) {
            return 0.seconds
        }
        val timestampAge = (nowNanos - playbackPositionTimestamp.nanoTime)
            .toDuration(DurationUnit.NANOSECONDS)
        val framesElapsed = playbackPositionTimestamp.framePosition + audioProcessor.skippedFrames
        val position = framesElapsed / track.sampleRate.toDouble()
        return position.toDuration(DurationUnit.SECONDS) + timestampAge
    }

    @OptIn(UnstableApi::class)
    fun estimateBufferFullnessAndSize(): Pair<Int, Int> {
        val bufferedDurationMs = (player.bufferedPosition - player.currentPosition)
            .toInt()
            .takeIf { it >= 0 }
            ?: 0
        // At a maximum, the player will rebuffer its max buffer duration when it reaches its
        // min buffer duration, so the maximum buffered duration is the sum of both
        val maxBufferDurationMs =
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS +
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
        return bufferedDurationMs to maxBufferDurationMs
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        Log.d(TAG, "Playback state change $lastPlaybackState -> $playbackState")
        when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY ->
                onPlaybackReady(playbackState == Player.STATE_BUFFERING)

            Player.STATE_ENDED -> onPlaybackEnded(true)

            Player.STATE_IDLE -> {
                if (player.playerError != null) {
                    onPlaybackError()
                } else if (lastPlaybackState != Player.STATE_ENDED) {
                    onPlaybackEnded(false)
                }
            }
        }
        updatePlayerVolume(false)
        lastPlaybackState = playbackState
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        if (readyForPlayback) {
            onPauseStateChanged(!playWhenReady)
        }
        updatePlayerVolume(false)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        mediaMetadata.title?.let { onMetadataReceived(it, mediaMetadata.artworkUri) }
    }

    private fun updatePlayerVolume(isSetVolume: Boolean) {
        val volume = lastSetVolume ?: return
        val isPlaying = readyForPlayback && !paused
        val mode = prefs.localPlayerVolumeMode
        when {
            isSetVolume && mode == LocalPlayerVolumeMode.PlayerOnly -> {
                playerInternalVolume = volume
                player.volume = volume * currentReplayGain
            }

            isSetVolume && mode == LocalPlayerVolumeMode.Device -> {
                applyVolumeAsDeviceVolume(volume)
            }

            isPlaying && mode == LocalPlayerVolumeMode.DeviceWhilePlaying -> {
                if (lastSavedDeviceVolume == null) {
                    lastSavedDeviceVolume = player.deviceVolume
                }
                applyVolumeAsDeviceVolume(volume)
            }

            !isPlaying && lastSavedDeviceVolume != null -> {
                player.setDeviceVolume(lastSavedDeviceVolume!!, 0)
                lastSavedDeviceVolume = null
            }
        }
    }

    private fun applyVolumeAsDeviceVolume(volume: Float) {
        val maxVolume = player.deviceInfo.maxVolume
        val volumeAsInt = (volume * maxVolume).roundToInt()
        player.setDeviceVolume(volumeAsInt, 0)
    }

    @OptIn(UnstableApi::class)
    inner class AudioSinkOverridingFactory(context: Context) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean
        ) = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setAudioOutputProvider(audioOutputProvider)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessorChain(SkippingProcessorChain(audioProcessor))
            .build()
    }

    @UnstableApi
    class SkippingProcessorChain(private val processor: LocalPlayerAudioProcessor) :
        AudioProcessorChain {
        private val processors = arrayOf(processor)

        override fun getAudioProcessors() = processors

        override fun applyPlaybackParameters(playbackParameters: PlaybackParameters) =
            playbackParameters

        override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean) = skipSilenceEnabled

        override fun getMediaDuration(playoutDuration: Long) = playoutDuration

        override fun getSkippedOutputFrameCount() = processor.skippedFrames
    }

    @UnstableApi
    class NetworkTransferListener : TransferListener {
        var totalBytesTransferred = 0L

        override fun onTransferInitializing(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) {
        }

        override fun onTransferStart(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) {
        }

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int
        ) {
            if (isNetwork) {
                totalBytesTransferred += bytesTransferred
            }
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) {
        }
    }

    companion object {
        private const val TAG = "LocalPlayer"
    }
}
