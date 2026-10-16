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

package de.maniac103.squeezeclient.ui.playermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.ui.composables.TextResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.collections.get

sealed class PlayerManagementUiState {
    data object Disconnected : PlayerManagementUiState()
    data object Loading : PlayerManagementUiState()
    data class Ready(
        val players: List<PlayerData>,
        val slavesByMaster: Map<PlayerId, List<PlayerId>>
    ) : PlayerManagementUiState()
}

@OptIn(ExperimentalStdlibApi::class)
class PlayerManagementViewModel(
    private val connectionHelper: ConnectionHelper
) : ViewModel() {
    private val uiStateFlowInternal = MutableStateFlow<PlayerManagementUiState>(
        PlayerManagementUiState.Loading
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val playerInfoBackendFlow = connectionHelper.state
        .mapNotNull { (it as? ConnectionState.Connected)?.players }
        .flatMapMerge { players ->
            val playerIdAndStatusFlows = players.map { p ->
                connectionHelper.playerState(p.id).flatMapLatest { state ->
                    combine(flowOf(p), flowOf(state.playerId), state.playStatus) { data, id, ps ->
                        Triple(data, id, ps)
                    }
                }
            }
            combine(playerIdAndStatusFlows) { it.toList() }
        }
        .map { playerIdsAndStatus ->
            val playerData = playerIdsAndStatus
                .map { (playerData, playerId, status) ->
                    val master = if (status.syncMaster != playerId) status.syncMaster else null
                    val nowPlayingInfo = if (master == null) status.playlist.nowPlaying else null
                    val playbackState = if (master == null) status.playbackState else null
                    val modelName = PLAYER_NAME_MAP[playerData.model]
                        ?.let { TextResource.Raw(it) }
                        ?: TextResource.StringRes(R.string.player_name_software_player, playerData.model)
                    PlayerData(
                        playerId,
                        status.playerName,
                        modelName,
                        playerData.canPowerOff,
                        nowPlayingInfo,
                        playbackState,
                        status.currentVolume,
                        status.powered,
                        master
                    )
                }
            val masterNames = playerData
                .filter { it.master == null }
                .associate { it.playerId to it.name }
                .toMap()
            playerData.sortedWith { a, b ->
                when {
                    // Both are masters -> compare by their name
                    a.master == null && b.master == null -> a.name.compareTo(b.name)

                    // Both are slaves to the same master -> compare by their name
                    a.master == b.master -> a.name.compareTo(b.name)

                    // A is slave of B
                    a.master == b.playerId -> 1

                    // B is slave of A
                    b.master == a.playerId -> -1

                    // Both are slaves to different masters -> compare master names
                    a.master != null && b.master != null -> {
                        val aMasterName = requireNotNull(masterNames[a.master])
                        val bMasterName = requireNotNull(masterNames[b.master])
                        aMasterName.compareTo(bMasterName)
                    }

                    // A is master, but B isn't -> compare name of A to master name of B
                    a.master == null -> a.name.compareTo(requireNotNull(masterNames[b.master]))

                    // B is master, but A isn't -> compare name of B to master name of A
                    else -> requireNotNull(masterNames[a.master]).compareTo(b.name)
                }
            }
        }
        .distinctUntilChanged()

    val uiStateFlow = uiStateFlowInternal.asStateFlow()

    init {
        viewModelScope.launch {
            connectionHelper.state.collectLatest { state ->
                when (state) {
                    is ConnectionState.Disconnected -> {
                        uiStateFlowInternal.emit(PlayerManagementUiState.Disconnected)
                    }

                    is ConnectionState.ConnectionFailure -> {
                        uiStateFlowInternal.emit(PlayerManagementUiState.Disconnected)
                    }

                    is ConnectionState.Connecting -> {
                        uiStateFlowInternal.emit(PlayerManagementUiState.Loading)
                    }

                    is ConnectionState.Connected -> {
                        // We'll update to Ready from the subscription below
                    }
                }
            }
        }

        viewModelScope.launch {
            playerInfoBackendFlow.collectLatest { playerData ->
                val slavesByMaster = mutableMapOf<PlayerId, MutableList<PlayerId>>()
                playerData.forEach { player ->
                    if (player.master != null) {
                        slavesByMaster
                            .getOrPutIfMissing(player.master, { mutableListOf() })
                            .add(player.playerId)
                    }
                }

                uiStateFlowInternal.emit(
                    PlayerManagementUiState.Ready(playerData, slavesByMaster)
                )
            }
        }
    }

    fun togglePlayerPower(playerId: PlayerId) = viewModelScope.launch {
        connectionHelper.togglePower(playerId)
    }

    fun setPlayerPlayState(
        playerId: PlayerId,
        newState: PlayerStatus.PlayState
    ) = viewModelScope.launch {
        connectionHelper.changePlaybackState(playerId, newState)
    }

    fun setPlayerVolume(playerId: PlayerId, newVolume: Int) = viewModelScope.launch {
        connectionHelper.setVolume(playerId, newVolume)
    }

    fun syncPlayers(master: PlayerId, slave: PlayerId) = viewModelScope.launch {
        connectionHelper.syncPlayers(master, slave)
    }

    fun unsyncPlayer(playerId: PlayerId) = viewModelScope.launch {
        connectionHelper.unsyncPlayer(playerId)
    }

    companion object {
        private val PLAYER_NAME_MAP = mapOf(
            "baby" to "Squeezebox Radio",
            "boom" to "Squeezebox Boom",
            "receiver" to "Squeezebox Receiver",
            "controller" to "Squeezebox Controller",
            "fab4" to "Squeezebox Touch",
            "squeezebox" to "Squeezebox 1",
            "squeezebox2" to "Squeezebox 2",
            "squeezebox3" to "Squeezebox Classic",
            "slimp3" to "SliMP3",
            "transporter" to "Transporter"
        )
    }
}