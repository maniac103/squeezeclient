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

package de.maniac103.squeezeclient.ui.volume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.PlayerId
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class VolumePopupViewModel(
    private val connectionHelper: ConnectionHelper,
    private val playerId: PlayerId
) : ViewModel() {
    private val currentPlayerStateFlow = connectionHelper.playerState(playerId)
        .flatMapLatest { it.playStatus }
    private val visibleFlowInternal = MutableStateFlow(false)

    val stateFlow = currentPlayerStateFlow
        .map { it.volume }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val visibleFlow = visibleFlowInternal.asStateFlow()

    // FIXME: handle already showing
    fun show(duration: Duration) = viewModelScope.launch {
        if (stateFlow.value != null) {
            visibleFlowInternal.emit(true)
            delay(duration)
            visibleFlowInternal.emit(false)
        }
    }

    fun setVolume(volume: Int) = viewModelScope.launch {
        connectionHelper.setVolume(playerId, volume)
    }

    fun adjustVolume(delta: Int) {
        val currentVolume = stateFlow.value?.volume
            ?: return
        setVolume(currentVolume + delta)
    }

    fun setMuted(muted: Boolean) = viewModelScope.launch {
        connectionHelper.setMuteState(playerId, muted)
    }
}
