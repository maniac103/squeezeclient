/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

package de.maniac103.squeezeclient.service.mediasession

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.request.PlaybackButtonRequest
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.volumeStepSize
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.Playlist
import kotlin.time.DurationUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@UnstableApi
class SqueezeboxMediaPlayer(
    private val appContext: Context,
    private val connectionHelper: ConnectionHelper,
    private val lifecycle: Lifecycle
) : SimpleBasePlayer(Looper.getMainLooper()),
    CoroutineScope by lifecycle.coroutineScope {
    var currentPlayer: PlayerId? = null
        set(value) {
            if (field != value) {
                field = value
                updatePlayer(value)
            }
        }
    var isConnectedToServer: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                updatePlayer(currentPlayer)
                invalidateState()
            }
        }
    private var latestStatus: PlayerStatus? = null
    private var latestPlaylist: Playlist? = null
    private var statusSubscription: Job? = null

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        connectionHelper.setVolume(playerId, deviceVolume)
    }

    override fun handleIncreaseDeviceVolume(flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        val currentVolume = latestStatus?.currentVolume ?: return@future
        val stepSize = appContext.prefs.volumeStepSize
        connectionHelper.setVolume(playerId, currentVolume + stepSize)
    }

    override fun handleDecreaseDeviceVolume(flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        val currentVolume = latestStatus?.currentVolume ?: return@future
        val stepSize = appContext.prefs.volumeStepSize
        connectionHelper.setVolume(playerId, currentVolume - stepSize)
    }

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        connectionHelper.setMuteState(playerId, muted)
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) = future {
        val playerId = currentPlayer ?: return@future
        val newState = when {
            playWhenReady -> PlayerStatus.PlayState.Playing
            else -> PlayerStatus.PlayState.Paused
        }
        connectionHelper.changePlaybackState(playerId, newState)
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int) = future {
        val playerId = currentPlayer ?: return@future
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT ->
                connectionHelper.sendButtonRequest(PlaybackButtonRequest.NextTrack(playerId))

            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS ->
                connectionHelper.sendButtonRequest(
                    PlaybackButtonRequest.PreviousTrack(playerId)
                )

            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                val positionSeconds = ((positionMs + 500) / 1000).toInt()
                connectionHelper.updatePlaybackPosition(playerId, positionSeconds)
            }

            COMMAND_SEEK_TO_MEDIA_ITEM -> {
                val positionSeconds = ((positionMs + 500) / 1000).toInt()
                connectionHelper.advanceToPlaylistPosition(playerId, mediaItemIndex)
                if (positionSeconds > 0) {
                    connectionHelper.updatePlaybackPosition(playerId, positionSeconds)
                }
            }

            else -> {}
        }
    }

    override fun handleStop() = future {
        val playerId = currentPlayer ?: return@future
        connectionHelper.changePlaybackState(playerId, PlayerStatus.PlayState.Stopped)
    }

    override fun getState(): State {
        val status = latestStatus
            ?: return waitingForPlayerState()
        val currentSong = status.playlist.nowPlaying
            ?: return State.Builder().setPlaybackState(STATE_IDLE).build()

        val currentSongDurationUs =
            status.currentSongDuration?.toLong(DurationUnit.MICROSECONDS)
        val (playlist, currentIndex) = latestPlaylist?.let { list ->
            val currentPosition = status.playlist.currentPosition - 1
            val mediaList: List<MediaItemData> = list.items.mapIndexed { index, item ->
                val builder = if (index + list.offset == currentPosition) {
                    // Prefer current song from status over playlist item, because the former
                    // may be more up to date (e.g. in case of radio streams)
                    currentSong.toMediaItemDataBuilder(index).apply {
                        currentSongDurationUs?.let { setDurationUs(it) }
                    }
                } else {
                    item.toMediaItemDataBuilder(index)
                }
                builder.build()
            }
            Pair(mediaList, currentPosition - list.offset)
        } ?: currentSong.let { song ->
            val builder = song.toMediaItemDataBuilder(0)
            currentSongDurationUs?.let { builder.setDurationUs(it) }
            Pair(listOf(builder.build()), 0)
        }

        val commandsBuilder = Player.Commands.Builder().apply {
            add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            add(COMMAND_GET_METADATA)
            add(COMMAND_GET_TIMELINE)
            add(COMMAND_PLAY_PAUSE)
            if (status.currentSongDuration != null &&
                status.playbackState != PlayerStatus.PlayState.Stopped
            ) {
                add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            }
            add(COMMAND_SEEK_TO_MEDIA_ITEM)
            add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            add(COMMAND_SEEK_TO_NEXT)
            add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            add(COMMAND_SEEK_TO_PREVIOUS)
            add(COMMAND_STOP)
            if (status.currentVolume != null) {
                add(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                add(COMMAND_GET_DEVICE_VOLUME)
                add(COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
            }
        }

        val playWhenReady = status.playbackState == PlayerStatus.PlayState.Playing
        val playbackState = when {
            !isConnectedToServer -> STATE_BUFFERING

            !status.powered -> STATE_IDLE

            else -> when (status.playbackState) {
                PlayerStatus.PlayState.Playing -> STATE_READY
                PlayerStatus.PlayState.Paused -> STATE_READY
                PlayerStatus.PlayState.Stopped -> STATE_IDLE
            }
        }

        val builder = State.Builder()
            .setPlaybackState(playbackState)
            .setAvailableCommands(commandsBuilder.build())
            .setContentPositionMs(
                status.currentPlayPosition?.toLong(DurationUnit.MILLISECONDS) ?: C.TIME_UNSET
            )
            .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(currentIndex)

        status.currentVolume?.let {
            builder.setDeviceVolume(it)
            builder.setDeviceInfo(
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                    .setMinVolume(0)
                    .setMaxVolume(100)
                    .build()
            )
        }
        status.muted?.let { builder.setIsDeviceMuted(it) }

        return builder.build()
    }

    /**
     * State to report while nothing is known about the controlled player yet (e.g. right
     * after the service has been started). A buffering state with a placeholder item is
     * reported instead of an empty state, because the media session only becomes visible
     * to the system (and the service only gets into the foreground) if it has a timeline
     * with a playing/buffering item.
     */
    private fun waitingForPlayerState(): State {
        val placeholder = MediaItemData.Builder(0)
            .setMediaItem(MediaItem.Builder().setMediaId("waitingForPlayer").build())
            .build()
        return State.Builder()
            .setPlaybackState(STATE_BUFFERING)
            .setPlayWhenReady(true, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaylist(listOf(placeholder))
            .setCurrentMediaItemIndex(0)
            .build()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updatePlayer(playerId: PlayerId?) {
        statusSubscription?.cancel()
        if (playerId == null || !isConnectedToServer) {
            return
        }
        statusSubscription = launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.playStatus }
                    .collect { status ->
                        if (status.playlist.lastChange != latestStatus?.playlist?.lastChange) {
                            latestPlaylist = connectionHelper.fetchPlaylist(
                                playerId,
                                PagingParams.All
                            )
                        }
                        latestStatus = status
                        invalidateState()
                    }
            }
        }
    }

    private fun Playlist.PlaylistItem.toMediaItemDataBuilder(position: Int): MediaItemData.Builder {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(extractIconUrl(appContext)?.toUri())
            .build()
        return MediaItemData.Builder(position)
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId(position.toString())
                    .setMediaMetadata(metadata)
                    .build()
            )
            .setMediaMetadata(metadata)
    }
}
