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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.itemlist.ItemPagingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val playerId = savedStateHandle.get<PlayerId>("playerId")!!

    private var lastKnownPlaylistTimestamp: Instant? = null
    private val uiStateFlowInternal = MutableStateFlow(UiState(0, null))
    private val scrollRequestFlowInternal = MutableSharedFlow<Int>()

    private val pagingSourceFactory = InvalidatingPagingSourceFactory {
        ItemPagingSource { page ->
            application.connectionHelper.fetchPlaylist(playerId, page)
        }
    }

    val itemsFlow = Pager(
        PagingConfig(
            pageSize = 100,
            enablePlaceholders = false
        ),
        pagingSourceFactory = pagingSourceFactory
    )
        .flow
        .cachedIn(viewModelScope)
    val uiStateFlow = uiStateFlowInternal.asStateFlow()
    val scrollRequestFlow = scrollRequestFlowInternal.asFlow()

    init {
        viewModelScope.launch {
            application.connectionHelper.playerState(playerId)
                .flatMapLatest { it.playStatus }
                .collect { status ->
                    val lastPlaylistTimestamp = lastKnownPlaylistTimestamp
                    val newPlaylistTimestamp = status.playlist.lastChange
                    if (
                        lastPlaylistTimestamp != null &&
                        lastPlaylistTimestamp != newPlaylistTimestamp
                    ) {
                        pagingSourceFactory.invalidate()
                    }

                    lastKnownPlaylistTimestamp = newPlaylistTimestamp
                    uiStateFlowInternal.value = UiState(status.playlist.currentPosition - 1, null)
                }
        }
    }

    fun scrollToCurrentPlaylistPosition() = viewModelScope.launch {
        scrollRequestFlowInternal.emit(uiStateFlowInternal.value.currentPosition)
    }

    fun movePlaylistItem(from: Int, to: Int) = viewModelScope.launch {
        uiStateFlowInternal.update { it.copy(pendingMove = PendingMove(from, to)) }
        application.connectionHelper.movePlaylistItem(playerId, from, to)
    }

    fun setPlaylistPosition(position: Int) = viewModelScope.launch {
        application.connectionHelper.advanceToPlaylistPosition(playerId, position)
    }

    fun removePlaylistItem(position: Int) = viewModelScope.launch {
        application.connectionHelper.removePlaylistItem(playerId, position)
    }

    data class UiState(val currentPosition: Int, val pendingMove: PendingMove?)
    data class PendingMove(val from: Int, val to: Int)
}