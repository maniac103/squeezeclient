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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.media3.exoplayer.source.BundledExtractorsAdapter
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ProgressiveMediaExtractor
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.extfuncs.LocalPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.localPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.prefs
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import okhttp3.Response

class LocalPlayer(
    context: Context,
    private val onPlaybackReady: (buffering: Boolean) -> Unit = {},
    private val onPlaybackAdvancedToNextTrack: () -> Unit = {},
    private val onPauseStateChanged: (paused: Boolean) -> Unit = {},
    private val onPlaybackEnded: (streamEnded: Boolean) -> Unit = {},
    private val onPlaybackError: () -> Unit = {},
    private val onDecoderLoadFinished: () -> Unit = {},
    onDecodingFinished: () -> Unit = {},
    onAudioStreamFlushed: () -> Unit = {},
    private val onHeadersReceived: (response: Response) -> Unit = {},
    private val onMetadataReceived: (title: CharSequence, artworkUri: Uri?) -> Unit = { _, _ -> }
) : Player.Listener {
    private val prefs = context.prefs
    private val dataSourceFactory: HttpDataSource.Factory
    private val player: ExoPlayer
    private var lastPlaybackState = Player.STATE_IDLE
    private val flacMetadataCache = FlacMetadataCache()

    @UnstableApi
    private lateinit var transferListener: NetworkTransferListener

    var paused: Boolean
        get() = !player.playWhenReady
        set(value) {
            player.playWhenReady = !value
            // Remember the toggle: the volume changes the server sends around it are the
            // pause/resume fade and must not be applied to the device volume.
            lastPauseToggle = SystemClock.uptimeMillis()
            if (!value) {
                // Playback resumes at the volume the server last set, even if a pause fade moved
                // the player volume to zero.
                player.volume = playerInternalVolume * currentReplayGain
            }
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
            if (lastServerVolume?.let { abs(it - value) < 0.001f } == true) {
                // The server sends its volume again on each stream start. Don't apply it in that
                // case: the device volume might have been changed in the meantime (e.g. by a car
                // head unit) and adopted as current volume, and re-applying the server volume
                // would overwrite that change.
                return
            }
            // Don't apply the volume immediately: the server ramps the volume when pausing or
            // resuming playback, and each of those steps must not reach the device volume.
            val isNewChange = pendingServerVolume == null
            pendingServerVolume = value
            // Remember whether the change is a fade when the burst starts: the server ramps the
            // volume in steps, and the last of them can arrive after the fade window has passed.
            if (isNewChange) {
                pendingServerVolumeIsFade = isServerVolumeFade()
            }
            volumeChangeHandler.removeCallbacks(volumeChangeRunnable)
            volumeChangeHandler.postDelayed(volumeChangeRunnable, SERVER_VOLUME_CHANGE_DELAY)
        }

    private var lastSetVolume: Float? = null
    private var lastServerVolume: Float? = null
    private var playerInternalVolume = 1F
    private var currentReplayGain = 1F
    private var lastSavedDeviceVolume: Int? = null
    private var lastAppliedDeviceVolume: Int? = null
    private var lastDeviceVolumeChange = 0L
    private var lastPauseToggle = 0L
    private var pendingServerVolume: Float? = null
    private var pendingServerVolumeIsFade = false
    private val volumeChangeHandler = Handler(Looper.getMainLooper())
    private val restoreDeviceVolumeRunnable = Runnable { restoreSavedDeviceVolume() }
    private val volumeChangeRunnable = Runnable {
        val value = pendingServerVolume ?: return@Runnable
        pendingServerVolume = null
        if (pendingServerVolumeIsFade) {
            pendingServerVolumeIsFade = false
            // Fades must not be applied to the device volume: changing it would make connected
            // devices (e.g. car head units following the Bluetooth volume) show each step of the
            // ramp and pop up their volume slider. They must not become the volume the app
            // considers the server's volume either, as the server re-announces that volume when
            // the next stream starts - applying it then would overwrite a volume the user set.
            applyVolumeRamp(value)
        } else {
            lastServerVolume = value
            lastSetVolume = value
            updatePlayerVolume(true)
        }
    }

    /**
     * Whether a volume the server sent is a fade rather than a volume the user set: the server
     * fades the volume when pausing and resuming playback, and it also changes the volume while
     * no stream is playing (e.g. when stopping the stream). Neither must reach the device volume.
     */
    private fun isServerVolumeFade(): Boolean =
        SystemClock.uptimeMillis() - lastPauseToggle <= PAUSE_FADE_WINDOW_TIME ||
            !readyForPlaybackOrBuffering

    @UnstableApi
    private val audioProcessor = LocalPlayerAudioProcessor(
        resetCallback = onAudioStreamFlushed,
        endOfStreamCallback = onDecodingFinished
    )
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
        // Resume quickly after a rebuffer: when the next track hasn't fully buffered by the
        // time the current one ends, the default 2 s buffer-for-playback-after-rebuffer causes
        // an audible pause at the track boundary.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                500
            )
            .build()
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setRenderersFactory(AudioSinkOverridingFactory(context))
            .setLoadControl(loadControl)
            .setDeviceVolumeControlEnabled(true)
            .build()
        // The server sends the next track's stream command while the current track is still
        // playing. Pre-buffer it so it's ready once playback reaches the playlist item,
        // which is required for gapless track transitions.
        player.setPreloadConfiguration(
            ExoPlayer.PreloadConfiguration(30.seconds.inWholeMicroseconds)
        )
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
    fun play(
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

        val extractorFactory: ProgressiveMediaExtractor.Factory = { _ ->
            if (mimeType == null) {
                BundledExtractorsAdapter(DefaultExtractorsFactory())
            } else {
                LocalPlayerMediaExtractor(mimeType, flacMetadataCache)
            }
        }

        val mediaSourceFactory = ProgressiveMediaSource.Factory(
            dataSourceFactoryForHeaders,
            extractorFactory
        )
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        // A new stream cancels a pending restore of the device volume (see stop()).
        volumeChangeHandler.removeCallbacks(restoreDeviceVolumeRunnable)

        currentReplayGain = replayGain

        if (player.playbackState == Player.STATE_IDLE) {
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = autoStart
        } else {
            player.addMediaSource(mediaSource)
        }
    }

    fun stop() {
        player.stop()
        // Playback stopped: give the system volume the user had before playback back, but not
        // immediately - the server also stops the stream on track changes, where a new stream
        // follows right away and the device volume must stay untouched.
        volumeChangeHandler.removeCallbacks(restoreDeviceVolumeRunnable)
        volumeChangeHandler.postDelayed(restoreDeviceVolumeRunnable, DEVICE_VOLUME_RESTORE_DELAY)
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
        if (track?.getTimestamp(playbackPositionTimestamp) == true) {
            val timestampAge = (nowNanos - playbackPositionTimestamp.nanoTime)
                .toDuration(DurationUnit.NANOSECONDS)
            val framesElapsed =
                playbackPositionTimestamp.framePosition + audioProcessor.skippedFrames
            val position = framesElapsed / track.sampleRate.toDouble()
            return position.toDuration(DurationUnit.SECONDS) + timestampAge
        }
        // Fall back to ExoPlayer's own position estimate (e.g. when the AudioTrack
        // timestamp isn't available). The server needs a correct position to deliver the
        // next track in time for gapless playback.
        return player.currentPosition.coerceAtLeast(0).toDuration(DurationUnit.MILLISECONDS)
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
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS + DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
        return bufferedDurationMs to maxBufferDurationMs
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Log.d(TAG, "onMediaItemTransition(${mediaItem?.mediaId}, $reason)")
        super.onMediaItemTransition(mediaItem, reason)
        if (
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        ) {
            player.volume = playerInternalVolume * currentReplayGain
        }
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && readyForPlayback && !paused) {
            onPlaybackAdvancedToNextTrack()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        Log.d(
            TAG,
            "Playback state change $lastPlaybackState -> $playbackState",
            player.playerError?.takeIf { playbackState == Player.STATE_IDLE }
        )
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

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        // Check whether the change was caused by ourselves before the tracking below is updated,
        // and keep tracking it in all modes so writes that wouldn't change anything can be
        // skipped (see setDeviceVolume()).
        val isOwnChange = volume == lastAppliedDeviceVolume
        lastAppliedDeviceVolume = volume
        if (prefs.localPlayerVolumeMode != LocalPlayerVolumeMode.DeviceWhilePlaying) {
            // In this mode the player volume isn't translated to the device volume, so there
            // is nothing to adopt.
            return
        }
        if (isOwnChange) {
            // Change was caused by ourselves, don't adopt it.
            return
        }
        if (SystemClock.elapsedRealtime() - lastDeviceVolumeChange < DEVICE_VOLUME_SETTLE_TIME) {
            // The change was reported right after we applied a volume ourselves. Either it is
            // our own change being reported back, or it is one step of a volume ramp the server
            // performs (e.g. when pausing or resuming playback). Neither is a user initiated
            // change, so don't adopt it - adopting would make the next playback state change
            // apply a volume the user never chose.
            return
        }
        // The volume was changed externally, e.g. by a car head unit using Bluetooth absolute
        // volume. Adopt it as the desired volume, so it is not overwritten on the next
        // playback state change (e.g. the next track).
        val maxVolume = player.deviceInfo.maxVolume.takeIf { it > 0 } ?: return
        lastSetVolume = volume.toFloat() / maxVolume
        if (lastSavedDeviceVolume != null) {
            lastSavedDeviceVolume = volume
        }
        Log.d(TAG, "onDeviceVolumeChanged: adopted device volume $volume")
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        mediaMetadata.title?.let { onMetadataReceived(it, mediaMetadata.artworkUri) }
    }

    private fun updatePlayerVolume(isSetVolume: Boolean) {
        val volume = lastSetVolume ?: return
        if (paused) {
            // While playback is paused (which includes the fade the server applies when pausing),
            // only the player volume may follow the server volume. The device volume has to stay
            // untouched, as e.g. a car head unit would show it as a volume change.
            player.volume = volume * currentReplayGain
            return
        }
        // Playback is considered ongoing while playing or buffering to continue playing
        // (e.g. right after a seek).
        val playbackOngoing = readyForPlaybackOrBuffering && !paused
        val mode = prefs.localPlayerVolumeMode
        when {
            isSetVolume && mode == LocalPlayerVolumeMode.PlayerOnly -> {
                playerInternalVolume = volume
                player.volume = volume * currentReplayGain
            }

            isSetVolume && mode == LocalPlayerVolumeMode.Device -> {
                player.volume = playerInternalVolume * currentReplayGain
                applyVolumeAsDeviceVolume(volume)
            }

            playbackOngoing && mode == LocalPlayerVolumeMode.DeviceWhilePlaying -> {
                player.volume = playerInternalVolume * currentReplayGain
                if (player.deviceInfo.maxVolume > 0) {
                    if (lastSavedDeviceVolume == null) {
                        lastSavedDeviceVolume = player.deviceVolume
                    }
                    applyVolumeAsDeviceVolume(volume)
                }
            }
        }
    }

    /**
     * Applies a server-side volume ramp (as used when pausing and resuming playback) as a factor
     * of the volume the server ramps from. This keeps the ramp away from the device volume and
     * makes the player volume end up at the level it had before the ramp started.
     */
    private fun applyVolumeRamp(volume: Float) {
        val target = lastServerVolume ?: return
        val factor = if (target > 0.001f) (volume / target).coerceIn(0F, 1F) else 0F
        player.volume = factor * playerInternalVolume * currentReplayGain
        Log.d(TAG, "applyVolumeRamp: ramped to $volume (factor $factor)")
    }

    /**
     * Restores the device volume that was active before playback started. Called when playback
     * stopped for good - not when it merely pauses, as the device volume should stay untouched
     * while the pause toggle is in progress.
     */
    private fun restoreSavedDeviceVolume() {
        val savedVolume = lastSavedDeviceVolume ?: return
        Log.d(TAG, "restoreSavedDeviceVolume: restoring device volume $savedVolume")
        setDeviceVolume(savedVolume)
        lastSavedDeviceVolume = null
    }

    private fun applyVolumeAsDeviceVolume(volume: Float) {
        val maxVolume = player.deviceInfo.maxVolume.takeIf { it > 0 } ?: return
        val volumeAsInt = (volume * maxVolume).roundToInt()
        setDeviceVolume(volumeAsInt)
    }

    private fun setDeviceVolume(volume: Int) {
        if (volume == lastAppliedDeviceVolume) {
            // The volume doesn't change, so don't write it back: devices connected via Bluetooth
            // still report such a write as a volume change (and e.g. show their volume slider).
            return
        }
        lastAppliedDeviceVolume = volume
        lastDeviceVolumeChange = SystemClock.elapsedRealtime()
        player.setDeviceVolume(volume, 0)
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

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
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

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        }
    }

    companion object {
        private const val TAG = "LocalPlayer"

        // Time after setting the device volume ourselves during which device volume changes are
        // still attributed to that change (or to a server-side volume ramp) and not adopted as
        // user-set volume.
        private const val DEVICE_VOLUME_SETTLE_TIME = 1500L

        // Time after a stop before the device volume the user had before playback is restored.
        // Track changes stop and restart the stream right away, so this delay keeps their
        // interrupt short.
        private const val DEVICE_VOLUME_RESTORE_DELAY = 3000L

        // Time to wait for further server-side volume changes before applying them, so that the
        // pause/resume ramp of the server can be recognised as such.
        private const val SERVER_VOLUME_CHANGE_DELAY = 350L

        // Time after a pause toggle in which server-side volume changes are considered part of
        // the pause/resume fade, i.e. are applied to the player volume instead of the device
        // volume.
        private const val PAUSE_FADE_WINDOW_TIME = 1500L
    }
}
