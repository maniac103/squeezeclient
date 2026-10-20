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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.itemlist.ItemPagingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(
    private val connectionHelper: ConnectionHelper,
    private val playerId: PlayerId
) : ViewModel() {
    private val pendingMoveRequestsFlow = MutableSharedFlow<PendingMove>()
    private val scrollRequestFlowInternal = MutableSharedFlow<Int>()

    private val playStatusFlow = connectionHelper.playerState(playerId)
        .flatMapLatest { it.playStatus }

    val itemsFlow = playStatusFlow
        .map { it.playlist.lastChange }
        .distinctUntilChanged()
        .flatMapLatest {
            Pager(
                config = PagingConfig(
                    pageSize = 200,
                    initialLoadSize = 200,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    ItemPagingSource { page -> connectionHelper.fetchPlaylist(playerId, page) }
                }
            ).flow
        }
        .cachedIn(viewModelScope)

    val uiStateFlow = playStatusFlow
        .flatMapLatest { status ->
            val position = status.playlist.currentPosition - 1
            pendingMoveRequestsFlow
                .map { UiState(position, it) }
                .onStart { emit(UiState(position, null)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
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
