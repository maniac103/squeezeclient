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
 */
package de.maniac103.squeezeclient.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.request.PlaybackButtonRequest
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.PlayerStatus.PlayState
import de.maniac103.squeezeclient.model.PlayerStatus.RepeatState
import de.maniac103.squeezeclient.model.PlayerStatus.ShuffleState
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.TextResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.String
import kotlin.time.DurationUnit

class NowPlayingViewModel(
    private val connectionHelper: ConnectionHelper,
    val playerId: PlayerId,
) : ViewModel() {
    val contextMenuFlowInternal =
        MutableStateFlow<NowPlayingContextMenuState>(NowPlayingContextMenuState.Idle)
    val playlistSaveInputStateFlowInternal = MutableStateFlow(PlaylistSaveEditorState.Idle)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiStateFlow = connectionHelper
        .playerState(playerId)
        .flatMapLatest { it.playStatus }
        .map { convertToUiState(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )
    val contextMenuFlow = contextMenuFlowInternal.asStateFlow()
    val playlistSaveInputStateFlow = playlistSaveInputStateFlowInternal.asStateFlow()

    fun previous() = viewModelScope.launch {
        connectionHelper.sendButtonRequest(PlaybackButtonRequest.PreviousTrack(playerId))
    }

    fun next() = viewModelScope.launch {
        connectionHelper.sendButtonRequest(PlaybackButtonRequest.NextTrack(playerId))
    }

    fun toggleRepeat() = viewModelScope.launch {
        connectionHelper.sendButtonRequest(PlaybackButtonRequest.ToggleRepeat(playerId))
    }

    fun toggleShuffle() = viewModelScope.launch {
        connectionHelper.sendButtonRequest(PlaybackButtonRequest.ToggleShuffle(playerId))
    }

    fun setPlayPauseState(play: Boolean) = viewModelScope.launch {
        val newState = if (play) PlayState.Paused else PlayState.Playing
        connectionHelper.changePlaybackState(playerId, newState)
    }

    fun stop() = viewModelScope.launch {
        connectionHelper.changePlaybackState(playerId, PlayState.Stopped)
    }

    fun seekTo(positionSeconds: Int) = viewModelScope.launch {
        connectionHelper.updatePlaybackPosition(playerId, positionSeconds)
    }

    fun showPlaylistSaveInput() = viewModelScope.launch {
        playlistSaveInputStateFlowInternal.emit(PlaylistSaveEditorState.Show)
    }

    fun savePlaylist(name: String) = viewModelScope.launch {
        playlistSaveInputStateFlowInternal.emit(PlaylistSaveEditorState.Saving)
        connectionHelper.saveCurrentPlaylist(playerId, name)
        playlistSaveInputStateFlowInternal.emit(PlaylistSaveEditorState.Idle)
    }

    fun onPlaylistSaveInputDismissed() = viewModelScope.launch {
        playlistSaveInputStateFlowInternal.emit(PlaylistSaveEditorState.Idle)
    }

    fun clearPlaylist() = viewModelScope.launch {
        connectionHelper.clearCurrentPlaylist(playerId)
    }

    fun loadContextMenu(data: NowPlayingContextMenuData) = viewModelScope.launch {
        contextMenuFlowInternal.emit(NowPlayingContextMenuState.Loading)
        val items = connectionHelper.fetchItemsForAction(
            playerId,
            data.action,
            PagingParams.All,
            false
        )
        contextMenuFlowInternal.emit(
            NowPlayingContextMenuState.Show(items.items, data.item)
        )
    }

    fun onContextMenuItemSelected(
        contextItem: SlimBrowseItemList.SlimBrowseItem
    ): ContextMenuItemResult {
        val actions = contextItem.actions
        return when {
            actions?.doAction != null -> {
                val job = viewModelScope.launch {
                    connectionHelper.executeAction(playerId, actions.doAction)
                }
                ContextMenuItemResult.DoAction(job)
            }

            actions?.goAction != null -> {
                ContextMenuItemResult.GoAction(actions.goAction)
            }

            else -> ContextMenuItemResult.None
        }
    }

    fun onContextMenuDismissed() = viewModelScope.launch {
        contextMenuFlowInternal.emit(NowPlayingContextMenuState.Idle)
    }

    private fun convertToUiState(status: PlayerStatus) = NowPlayingUiState(
        title = status.playlist.nowPlaying?.title?.let { TextResource.Raw(it) }
            ?: TextResource.StringRes(R.string.nowplaying_empty_playlist),
        artist = status.playlist.nowPlaying?.artist?.takeIf { it.isNotEmpty() },
        album = status.playlist.nowPlaying?.album?.takeIf { it.isNotEmpty() },
        artwork = status.playlist.nowPlaying,
        positionSeconds = status.currentPlayPosition
            ?.toDouble(DurationUnit.SECONDS)
            ?.toFloat()
            ?: 0F,
        durationSeconds = status.currentSongDuration
            ?.toDouble(DurationUnit.SECONDS)
            ?.toFloat()
            ?: 0.1F,
        isPlaying = status.playbackState == PlayState.Playing,
        isPowered = status.powered,
        playPauseEnabled = status.playlist.nowPlaying != null,
        playPauseIconResId = when (status.playbackState) {
            PlayState.Playing -> R.drawable.ic_pause_24dp
            else -> R.drawable.ic_play_24dp
        },
        repeatIconResId = when (status.repeatState) {
            RepeatState.RepeatTitle -> R.drawable.ic_repeat_one_24dp
            RepeatState.RepeatAll -> R.drawable.ic_repeat_24dp
            RepeatState.Off -> R.drawable.ic_repeat_off_24dp
        },
        shuffleIconResId = when (status.shuffleState) {
            ShuffleState.ShuffleAlbum -> R.drawable.ic_shuffle_album_24dp
            ShuffleState.ShuffleSong -> R.drawable.ic_shuffle_song_24dp
            ShuffleState.Off -> R.drawable.ic_shuffle_off_24dp
        },
        canSeek = status.playbackState != PlayState.Stopped && status.currentSongDuration != null,
        playlistPosition = status.playlist.currentPosition,
        playlistLength = status.playlist.trackCount,
        playerName = status.playerName,
        contextMenuData = status.playlist.nowPlaying?.actions?.moreAction?.let { action ->
            NowPlayingContextMenuData(status.playlist.nowPlaying.asSlimbrowseItem(), action)
        }
    )
}

sealed interface ContextMenuItemResult {
    object None : ContextMenuItemResult
    data class DoAction(val job: Job) : ContextMenuItemResult
    data class GoAction(val action: JiveAction) : ContextMenuItemResult
}

sealed interface NowPlayingContextMenuState {
    object Idle : NowPlayingContextMenuState
    object Loading : NowPlayingContextMenuState
    data class Show(
        val items: List<SlimBrowseItemList.SlimBrowseItem>,
        val parentItem: SlimBrowseItemList.SlimBrowseItem
    ) : NowPlayingContextMenuState
}

enum class PlaylistSaveEditorState {
    Idle,
    Show,
    Saving
}

data class NowPlayingContextMenuData(
    val item: SlimBrowseItemList.SlimBrowseItem,
    val action: JiveAction
)

data class NowPlayingUiState(
    val title: TextResource,
    val artist: String?,
    val album: String?,
    val artwork: ArtworkItem?,
    val positionSeconds: Float,
    val durationSeconds: Float,
    val isPlaying: Boolean,
    val isPowered: Boolean,
    val playPauseEnabled: Boolean,
    val playPauseIconResId: Int,
    val repeatIconResId: Int,
    val shuffleIconResId: Int,
    val canSeek: Boolean,
    val playlistPosition: Int,
    val playlistLength: Int,
    val playerName: String,
    val contextMenuData: NowPlayingContextMenuData?
) {
    val toolbarSubtitle get() = TextResource.StringRes(
        R.string.nowplaying_subtitle, playerName, playlistPosition, playlistLength
    )
}