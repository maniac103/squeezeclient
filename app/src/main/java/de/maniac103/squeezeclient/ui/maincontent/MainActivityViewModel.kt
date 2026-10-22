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
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.ServerConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModel(
    private val connectionHelper: ConnectionHelper,
    private val preferences: Preferences
) : ViewModel() {
    private var connectionAttempts = 0
    private var lastSelectedPlayerAndConfig: Pair<ServerConfiguration?, Player>? = null

    private val stateSelectedPlayerAndConfigFlow = combine(
        connectionHelper.state,
        preferences.serverConfig.asFlow(),
        preferences.lastSelectedPlayer.asFlow()
    ) { state, serverConfig, selectedId -> Triple(state, serverConfig, selectedId) }

    val stateFlow = stateSelectedPlayerAndConfigFlow
        .map { (state, serverConfig, selectedId) ->
            if (lastSelectedPlayerAndConfig?.first != serverConfig) {
                // Invalidate last known player once server config changes
                lastSelectedPlayerAndConfig = null
            }
            val lastSelectedPlayer = lastSelectedPlayerAndConfig?.second

            when (state) {
                is ConnectionState.Disconnected -> {
                    if (connectionAttempts++ < 3) {
                        connectionHelper.connect()
                        MainActivityState.Loading(serverConfig, lastSelectedPlayer)
                    } else {
                        MainActivityState.CouldNotConnect(serverConfig, lastSelectedPlayer)
                    }
                }

                is ConnectionState.Connecting ->
                    MainActivityState.Loading(serverConfig, lastSelectedPlayer)

                is ConnectionState.Connected -> {
                    connectionAttempts = 0
                    val presentPlayers = state.players.filter { it.connected }
                    val selectedPlayer = presentPlayers.find { it.id == selectedId }
                        ?: presentPlayers.firstOrNull()

                    when {
                        serverConfig == null ->
                            MainActivityState.Loading(null, null)

                        selectedPlayer != null -> {
                            lastSelectedPlayerAndConfig = serverConfig to selectedPlayer
                            MainActivityState.Connected(
                                serverConfig,
                                presentPlayers,
                                selectedPlayer
                            )
                        }

                        else -> {
                            lastSelectedPlayerAndConfig = null
                            MainActivityState.NoPlayers(serverConfig)
                        }
                    }
                }

                is ConnectionState.ConnectionFailure -> {
                    MainActivityState.ConnectionFailed(
                        serverConfig,
                        lastSelectedPlayer,
                        state.cause
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = MainActivityState.Loading(null, null)
        )

    val selectedPlayerActiveStateFlow = stateFlow
        .flatMapLatest { state ->
            (state as? MainActivityState.Connected)?.selectedPlayer?.id
                ?.let { playerId ->
                    connectionHelper.playerState(playerId)
                        .flatMapLatest { it.playStatus }
                        .map {
                            SelectedPlayerActiveState(
                                playerId,
                                it.playbackState == PlayerStatus.PlayState.Playing
                            )
                        }
                }
                ?: flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val forceGridForSlimBrowseItemsFlow = preferences.forceGridLayout.asFlow()
    val localPlaybackEnabledFlow = preferences.localPlayerEnabled.asFlow()

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
    val serverConfig: ServerConfiguration?
    val selectedPlayer: Player?

    data class Loading(
        override val serverConfig: ServerConfiguration?,
        override val selectedPlayer: Player?
    ) : MainActivityState

    data class NoPlayers(
        override val serverConfig: ServerConfiguration,
        override val selectedPlayer: Player? = null
    ) : MainActivityState

    data class Connected(
        override val serverConfig: ServerConfiguration,
        val players: List<Player>,
        override val selectedPlayer: Player
    ) : MainActivityState

    data class CouldNotConnect(
        override val serverConfig: ServerConfiguration?,
        override val selectedPlayer: Player?
    ) : MainActivityState

    data class ConnectionFailed(
        override val serverConfig: ServerConfiguration?,
        override val selectedPlayer: Player?,
        val cause: Throwable
    ) : MainActivityState
}

data class SelectedPlayerActiveState(val playerId: PlayerId, val isPlaying: Boolean)
