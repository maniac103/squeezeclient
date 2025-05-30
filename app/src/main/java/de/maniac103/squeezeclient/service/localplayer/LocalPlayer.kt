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
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.extfuncs.LocalPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.localPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.prefs
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import okhttp3.Response

class LocalPlayer(
    context: Context,
    private val onPlaybackStarted: () -> Unit = {},
    private val onPauseStateChanged: (paused: Boolean) -> Unit = {},
    private val onPlaybackEnded: () -> Unit = {},
    private val onHeadersReceived: (response: Response) -> Unit = {},
    private val onMetadataReceived: (title: CharSequence, artworkUri: Uri?) -> Unit = { _, _ -> }
) : Player.Listener {
    private val prefs = context.prefs
    private val dataSourceFactory: HttpDataSource.Factory
    private val player: ExoPlayer
    private var lastPlaybackState = Player.STATE_IDLE

    @UnstableApi
    private lateinit var statsListener: PlaybackStatsListener

    var paused: Boolean
        get() = !player.playWhenReady
        set(value) {
            player.playWhenReady = !value
        }

    val playingTitle get() = player.mediaMetadata.title

    val stats
        @OptIn(UnstableApi::class)
        get() = statsListener.combinedPlaybackStats
    val playbackPosition get() = player.currentPosition.toDuration(DurationUnit.MILLISECONDS)

    val readyForPlayback get() =
        player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING

    var volume: Float
        get() = lastSetVolume ?: 0F
        set(value) {
            lastSetVolume = value
            updatePlayerVolume(true)
        }

    private var lastSetVolume: Float? = null
    private var lastSavedDeviceVolume: Int? = null

    init {
        val client = context.httpClient.newBuilder()
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                onHeadersReceived(response)
                response
            }
            .build()
        dataSourceFactory = OkHttpDataSource.Factory(client)
        initStatsListener()
        player = initPlayer(context)
    }

    @OptIn(UnstableApi::class)
    private fun initStatsListener() {
        statsListener = PlaybackStatsListener(false) { _, _ -> }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer(context: Context): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setDeviceVolumeControlEnabled(true)
            .build()
        player.addListener(this)
        if (BuildConfig.DEBUG) {
            player.addAnalyticsListener(EventLogger())
        }
        player.addAnalyticsListener(statsListener)
        return player
    }

    @OptIn(UnstableApi::class)
    fun start(uri: Uri, mimeType: String?, headers: Map<String, String>, autoStart: Boolean) {
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
        player.stop()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = autoStart
    }

    fun stop() {
        player.stop()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        Log.d(TAG, "Playback state change $lastPlaybackState -> $playbackState")
        when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY ->
                if (lastPlaybackState in arrayOf(Player.STATE_IDLE, Player.STATE_ENDED)) {
                    onPlaybackStarted()
                }
            Player.STATE_ENDED -> onPlaybackEnded()
            Player.STATE_IDLE -> {}
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
            isSetVolume && mode == LocalPlayerVolumeMode.PlayerOnly ->
                player.volume = volume
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

    companion object {
        private const val TAG = "LocalPlayer"
    }
}
