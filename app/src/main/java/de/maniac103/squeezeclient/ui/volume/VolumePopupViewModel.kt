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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class VolumePopupViewModel(
    private val connectionHelper: ConnectionHelper,
    private val playerId: PlayerId
) : ViewModel() {
    var volumeControlSupported = false
        private set
    private val currentPlayerVolumeFlowInternal = MutableStateFlow(0F)
    private val currentMutedFlowInternal = MutableStateFlow(false)
    private var updateJob: Job? = null

    val volumeFlow = currentPlayerVolumeFlowInternal.asStateFlow()
    val mutedFlow = currentMutedFlowInternal.asStateFlow()

    init {
        viewModelScope.launch {
            connectionHelper.playerState(playerId)
                .flatMapLatest { it.playStatus }
                .collect { status ->
                    volumeControlSupported = status.currentVolume != null
                    status.currentVolume?.let {
                        currentPlayerVolumeFlowInternal.value = it.toFloat() / 100F
                    }
                    status.muted?.let {
                        currentMutedFlowInternal.value = it
                    }
                }
        }
    }

    fun setVolume(volume: Float) {
        currentPlayerVolumeFlowInternal.value = volume
        updateJob = viewModelScope.launch {
            connectionHelper.setVolume(playerId, (volume * 100F).roundToInt())
        }
    }

    fun adjustVolume(delta: Float) {
        val newVolume = (currentPlayerVolumeFlowInternal.value + delta).coerceIn(0F, 1F)
        setVolume(newVolume)
    }

    fun setMuted(muted: Boolean) {
        currentMutedFlowInternal.value = muted
        viewModelScope.launch {
            connectionHelper.setMuteState(playerId, muted)
        }
    }
}