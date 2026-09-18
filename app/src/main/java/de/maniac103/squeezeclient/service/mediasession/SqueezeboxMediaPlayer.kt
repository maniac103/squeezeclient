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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
                launch {
                    if (!value) {
                        // Don't report loss of connection immediately: reconnection may already
                        // be happening, and we do not want to report intermediate states
                        delay(2.seconds)
                    }
                    invalidateState()
                }
            }
        }
    private var pendingPlayerState = PendingPlayerState()
    private var playerState: PlayerState? = null
    private var unacknowledgedStateChange: UnacknowledgedPlayerStateChange? = null
    private var statusSubscription: Job? = null
    private var playlistFetchJob: Job? = null
    private var delayedStateUpdateJob: Job? = null
    private var unacknowledgedStateRevertJob: Job? = null

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        connectionHelper.setVolume(playerId, deviceVolume)
    }

    override fun handleIncreaseDeviceVolume(flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        val currentVolume = playerState?.currentVolume ?: return@future
        val stepSize = appContext.prefs.volumeStepSize
        connectionHelper.setVolume(playerId, currentVolume + stepSize)
    }

    override fun handleDecreaseDeviceVolume(flags: Int) = future {
        val playerId = currentPlayer ?: return@future
        val currentVolume = playerState?.currentVolume ?: return@future
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
        updateUnacknowledgedState(playState = newState)
        connectionHelper.changePlaybackState(playerId, newState)
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int) = future {
        val playerId = currentPlayer ?: return@future
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT -> {
                updateUnacknowledgedState(playlistPositionOffset = 1)
                connectionHelper.sendButtonRequest(PlaybackButtonRequest.NextTrack(playerId))
            }

            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS -> {
                updateUnacknowledgedState(playlistPositionOffset = -1)
                connectionHelper.sendButtonRequest(
                    PlaybackButtonRequest.PreviousTrack(playerId)
                )
            }

            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                val positionSeconds = ((positionMs + 500) / 1000).toInt()
                updateUnacknowledgedState(positionInTrack = positionMs.milliseconds)
                connectionHelper.updatePlaybackPosition(playerId, positionSeconds)
            }

            COMMAND_SEEK_TO_MEDIA_ITEM -> {
                val positionSeconds = ((positionMs + 500) / 1000).toInt()
                updateUnacknowledgedState(
                    absolutePlaylistPosition = mediaItemIndex,
                    positionInTrack = positionMs.milliseconds
                )
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
        updateUnacknowledgedState(playState = PlayerStatus.PlayState.Stopped)
        connectionHelper.changePlaybackState(playerId, PlayerStatus.PlayState.Stopped)
    }

    override fun getState(): State {
        val playerState = playerState
            ?: return waitingForPlayerState()
        val currentSong = playerState.currentSong
            ?: return State.Builder().setPlaybackState(STATE_IDLE).build()
        val unacknowledgedChange = unacknowledgedStateChange

        val currentSongDurationUs =
            playerState.currentSongDuration?.toLong(DurationUnit.MICROSECONDS)
        val (playlist, currentIndex) = playerState.playlist?.let { list ->
            val currentPosition = when {
                unacknowledgedChange?.absolutePlaylistPosition != null ->
                    unacknowledgedChange.absolutePlaylistPosition

                unacknowledgedChange?.playlistPositionOffset != null ->
                    playerState.playlistPosition + unacknowledgedChange.playlistPositionOffset

                else -> playerState.playlistPosition
            }
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
            if (playerState.currentSongDuration != null &&
                playerState.playbackState != PlayerStatus.PlayState.Stopped
            ) {
                add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            }
            add(COMMAND_SEEK_TO_MEDIA_ITEM)
            add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            add(COMMAND_SEEK_TO_NEXT)
            add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            add(COMMAND_SEEK_TO_PREVIOUS)
            add(COMMAND_STOP)
            if (playerState.currentVolume != null) {
                add(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                add(COMMAND_GET_DEVICE_VOLUME)
                add(COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
            }
        }

        val playWhenReady = playerState.playbackState == PlayerStatus.PlayState.Playing
        val playbackState = when {
            !isConnectedToServer -> STATE_BUFFERING

            !playerState.powered -> STATE_IDLE

            else -> when (unacknowledgedChange?.playState ?: playerState.playbackState) {
                PlayerStatus.PlayState.Playing -> STATE_READY
                PlayerStatus.PlayState.Paused -> STATE_READY
                PlayerStatus.PlayState.Stopped -> STATE_IDLE
            }
        }

        val builder = State.Builder()
            .setPlaybackState(playbackState)
            .setAvailableCommands(commandsBuilder.build())
            .setContentPositionMs(
                unacknowledgedChange?.positionInTrack?.inWholeMilliseconds
                    ?: playerState.currentPlayPosition?.toLong(DurationUnit.MILLISECONDS)
                    ?: C.TIME_UNSET
            )
            .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(currentIndex)

        playerState.currentVolume?.let {
            builder.setDeviceVolume(it)
            builder.setDeviceInfo(
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                    .setMinVolume(0)
                    .setMaxVolume(100)
                    .build()
            )
        }
        playerState.muted?.let { builder.setIsDeviceMuted(it) }

        return builder.build()
    }

    private fun updateUnacknowledgedState(
        absolutePlaylistPosition: Int? = null,
        playlistPositionOffset: Int? = null,
        positionInTrack: Duration? = null,
        playState: PlayerStatus.PlayState? = null
    ) {
        val newPlayState = playState ?: unacknowledgedStateChange?.playState
        val newPositionInTrack = positionInTrack ?: unacknowledgedStateChange?.positionInTrack
        val newAbsolutePosition = absolutePlaylistPosition
            ?: unacknowledgedStateChange?.absolutePlaylistPosition
        val existingOffset = unacknowledgedStateChange?.playlistPositionOffset
        val newOffset = when {
            // absolute position takes precedence
            newAbsolutePosition != null -> null

            playlistPositionOffset != null && existingOffset != null ->
                existingOffset + playlistPositionOffset

            playlistPositionOffset != null -> playlistPositionOffset

            else -> existingOffset
        }
        val playlistLength = pendingPlayerState.playlist?.totalCount ?: Int.MAX_VALUE

        unacknowledgedStateChange = UnacknowledgedPlayerStateChange(
            newAbsolutePosition
                ?.plus(playlistPositionOffset ?: 0)
                ?.coerceIn(0, playlistLength),
            newOffset?.coerceIn(0, playlistLength),
            newPositionInTrack,
            newPlayState
        )
        invalidateState()

        unacknowledgedStateRevertJob?.cancel()
        unacknowledgedStateRevertJob = launch {
            // Give the server some reasoanble amount of time to react
            delay(3.seconds)
            unacknowledgedStateChange = null
            invalidateState()
        }
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
                    .collect { status -> handlePlayerStatusUpdate(playerId, status) }
            }
        }
    }

    private fun handlePlayerStatusUpdate(playerId: PlayerId, status: PlayerStatus) {
        val latestStatus = pendingPlayerState.status
        val newPlaylistTimestamp = status.playlist.lastChange
        if (latestStatus != null && newPlaylistTimestamp < latestStatus.playlist.lastChange) {
            // The new status is older than what we already know about -> ignore it
            return
        }

        if (status.playlist.lastChange != latestStatus?.playlist?.lastChange) {
            playlistFetchJob?.cancel()
            playlistFetchJob = lifecycle.coroutineScope.launch {
                val playlist = connectionHelper.fetchPlaylist(playerId, PagingParams.All)
                if (playlist.timestamp == status.playlist.lastChange) {
                    pendingPlayerState.playlist = playlist
                    // Player state update is scheduled asynchronously so that the update method
                    // notices the playlist fetch being done
                    lifecycle.coroutineScope.launch {
                        schedulePlayerStateUpdate()
                    }
                }
            }
        }

        pendingPlayerState.status = status
        schedulePlayerStateUpdate()
    }

    private fun schedulePlayerStateUpdate() {
        delayedStateUpdateJob?.cancel()

        val status = pendingPlayerState.status ?: return
        val nowPlaying = playerState?.currentSong
        val newPlayerState = PlayerState(status, nowPlaying, pendingPlayerState.playlist)

        when {
            newPlayerState.isCompleteAndConsistent() || playerState == null -> {
                // Accept immediately if either
                // - we don't have a state yet (don't wait for playlist)
                // - or what we have looks consistent
                applyPlayerState(newPlayerState)
            }

            playlistFetchJob?.isActive == true -> {
                // Playlist is currently being fetched; we'll come here again once that is done
            }

            else -> {
                // Give the server a little more time for sending us consistent data:
                // When we come here we have received a status update, fetched the playlist and
                // the timestamps don't match. That means we'll likely get another status report
                // which will trigger another playlist fetch.
                delayedStateUpdateJob = lifecycle.coroutineScope.launch {
                    delay(500.milliseconds)
                    applyPlayerState(newPlayerState)
                }
            }
        }
    }

    private fun applyPlayerState(newPlayerState: PlayerState) {
        playerState = newPlayerState
        unacknowledgedStateChange = null
        unacknowledgedStateRevertJob?.cancel()
        invalidateState()
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
                    .setMediaId("$position-${hashCode()}")
                    .setMediaMetadata(metadata)
                    .build()
            )
            .setMediaMetadata(metadata)
    }

    private class PendingPlayerState {
        var status: PlayerStatus? = null
        var playlist: Playlist? = null
    }

    private data class PlayerState(
        private val status: PlayerStatus,
        private val nowPlayingFallback: Playlist.PlaylistItem?,
        val playlist: Playlist?
    ) {
        val currentVolume get() = status.currentVolume
        val currentSong get() = status.playlist.nowPlaying ?: nowPlayingFallback
        val currentSongDuration get() = status.currentSongDuration
        val currentPlayPosition get() = status.currentPlayPosition
        val playlistPosition get() = status.playlist.currentPosition - 1
        val playbackState get() = status.playbackState
        val powered get() = status.powered
        val muted get() = status.muted

        fun isCompleteAndConsistent() =
            playlist != null && status.playlist.lastChange == playlist.timestamp
    }

    private data class UnacknowledgedPlayerStateChange(
        val absolutePlaylistPosition: Int? = null,
        val playlistPositionOffset: Int? = null,
        val positionInTrack: Duration? = null,
        val playState: PlayerStatus.PlayState? = null
    )
}
