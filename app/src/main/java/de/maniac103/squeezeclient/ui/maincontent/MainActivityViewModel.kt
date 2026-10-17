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

package de.maniac103.squeezeclient.ui.maincontent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.model.Player
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivityViewModel(
    private val connectionHelper: ConnectionHelper,
    private val preferences: Preferences
) : ViewModel() {
    private val selectedPlayerFlowInternal = preferences.lastSelectedPlayer.asFlow()
    private val stateFlowInternal = MutableStateFlow<MainActivityState>(MainActivityState.Loading)
    private var connectionAttempts = 0

    val stateFlow = stateFlowInternal.asStateFlow()
    val serverConfigFlow = preferences.serverConfig.asFlow()
    val forceGridForSlimBrowseItemsFlow = preferences.forceGridLayout.asFlow()
    val localPlaybackEnabledFlow = preferences.localPlayerEnabled.asFlow()

    init {
        viewModelScope.launch {
            val stateAndSelectedPlayerFlow = combine(
                connectionHelper.state,
                selectedPlayerFlowInternal
            ) { state, selectedId -> state to selectedId }
            stateAndSelectedPlayerFlow.collect { (state, selectedId) ->
                when (state) {
                    is ConnectionState.Disconnected -> {
                        if (connectionAttempts++ < 3) {
                            connectionHelper.connect()
                            stateFlowInternal.emit(MainActivityState.Loading)
                        } else {
                            stateFlowInternal.emit(MainActivityState.CouldNotConnect)
                        }
                    }

                    is ConnectionState.Connecting -> {
                        stateFlowInternal.emit(MainActivityState.Loading)
                    }

                    is ConnectionState.Connected -> {
                        connectionAttempts = 0
                        val presentPlayers = state.players.filter { it.connected }
                        val selectedPlayer = selectedId
                            .let { selected -> presentPlayers.find { it.id == selected } }
                            ?: presentPlayers.firstOrNull()
                        if (selectedPlayer != null) {
                            stateFlowInternal.emit(
                                MainActivityState.Connected(presentPlayers, selectedPlayer)
                            )
                        } else {
                            stateFlowInternal.emit(MainActivityState.NoPlayers)
                        }
                    }

                    is ConnectionState.ConnectionFailure -> {
                        stateFlowInternal.emit(MainActivityState.ConnectionFailed(state.cause))
                    }
                }
            }
        }
    }

    fun retryConnection() = viewModelScope.launch {
        connectionAttempts = 0
        connectionHelper.connect()
    }

    fun selectPlayer(playerId: PlayerId) = viewModelScope.launch {
        preferences.lastSelectedPlayer.update(playerId)
    }

    fun setLocalPlaybackEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.localPlayerEnabled.update(enabled)
    }
}

sealed interface MainActivityState {
    data object Loading : MainActivityState
    data object NoPlayers : MainActivityState
    data class Connected(val players: List<Player>, val selectedPlayer: Player) : MainActivityState
    data object CouldNotConnect : MainActivityState
    data class ConnectionFailed(val cause: Throwable) : MainActivityState
}
