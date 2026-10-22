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

package de.maniac103.squeezeclient.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.itemlist.ItemPagingSource
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(
    private val connectionHelper: ConnectionHelper,
    private val playerId: PlayerId
) : ViewModel() {
    private val pendingMoveRequestsFlow = MutableSharedFlow<PendingMove>()
    private val scrollRequestFlowInternal = MutableSharedFlow<Int>()
    private val pagingSourceFactory = InvalidatingPagingSourceFactory {
        ItemPagingSource { page -> connectionHelper.fetchPlaylist(playerId, page) }
    }
    private var lastKnownPlaylistTimestamp: Instant? = null

    private val playStatusFlow = connectionHelper.playerState(playerId)
        .flatMapLatest { it.playStatus }

    private val pagingFlow = Pager(
        config = PagingConfig(pageSize = 200, initialLoadSize = 200, enablePlaceholders = false),
        pagingSourceFactory = pagingSourceFactory
    )
        .flow
        .cachedIn(viewModelScope)

    private val playlistTimestampMonitorFlow = playStatusFlow
        .map { it.playlist.lastChange }
        .distinctUntilChanged()
        .onEach { timestamp ->
            val last = lastKnownPlaylistTimestamp
            lastKnownPlaylistTimestamp = timestamp
            if (last != null && last != timestamp) {
                pagingSourceFactory.invalidate()
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 0)

    val itemsFlow = channelFlow {
        launch { playlistTimestampMonitorFlow.collect() }
        pagingFlow.collect { send(it) }
    }

    val uiStateFlow = playStatusFlow
        .flatMapLatest { status ->
            val position = status.playlist.currentPosition - 1
            pendingMoveRequestsFlow
                .map { UiState(position, it) }
                .onStart { emit(UiState(position, null)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = UiState(0, null)
        )

    val scrollRequestFlow = scrollRequestFlowInternal.asFlow()

    fun scrollToCurrentPlaylistPosition() = viewModelScope.launch {
        scrollRequestFlowInternal.emit(uiStateFlow.value.currentPosition)
    }

    fun movePlaylistItem(from: Int, to: Int) = viewModelScope.launch {
        pendingMoveRequestsFlow.emit(PendingMove(from, to))
        connectionHelper.movePlaylistItem(playerId, from, to)
    }

    fun setPlaylistPosition(position: Int) = viewModelScope.launch {
        connectionHelper.advanceToPlaylistPosition(playerId, position)
    }

    fun removePlaylistItem(position: Int) = viewModelScope.launch {
        connectionHelper.removePlaylistItem(playerId, position)
    }

    data class UiState(val currentPosition: Int, val pendingMove: PendingMove?)
    data class PendingMove(val from: Int, val to: Int)
}
