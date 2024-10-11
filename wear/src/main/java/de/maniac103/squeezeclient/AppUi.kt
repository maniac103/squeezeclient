/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.SettingsPower
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.gms.wearable.Node
import de.maniac103.squeezeclient.wearapi.PlayerInfo

@Composable
fun AppUi(
    viewModel: AppViewModel = viewModel(),
    onPlayerSelected: (PlayerInfo) -> Unit,
    onStopPlayers: () -> Unit,
    onTurnOffPlayers: () -> Unit,
    onDisconnect: () -> Unit
) {
    AppUi(
        connectionState = viewModel.connectionState.collectAsState().value,
        players = viewModel.players.collectAsState().value,
        activePlayerId = viewModel.activePlayerId.collectAsState().value,
        onPlayerSelected = onPlayerSelected,
        onStopPlayers = onStopPlayers,
        onTurnOffPlayers = onTurnOffPlayers,
        onDisconnect = onDisconnect
    )
}

@Composable
fun AppUi(
    connectionState: AppViewModel.ConnectionState,
    players: List<PlayerInfo>,
    activePlayerId: String,
    onPlayerSelected: (PlayerInfo) -> Unit,
    onStopPlayers: () -> Unit,
    onTurnOffPlayers: () -> Unit,
    onDisconnect: () -> Unit
) {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (connectionState) {
                is AppViewModel.ConnectionState.Disconnected -> {
                    ProgressSpinner()
                }

                is AppViewModel.ConnectionState.ConnectedToDevice -> {
                    ProgressSpinner()
                    Text(
                        text = stringResource(
                            R.string.status_connecting,
                            connectionState.node.displayName
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                is AppViewModel.ConnectionState.NoDeviceFound -> {
                    StatusMessage(R.string.status_no_device_found, null)
                }

                is AppViewModel.ConnectionState.DeviceAppIncompatible -> {
                    StatusMessage(R.string.status_app_incompatible, connectionState.node)
                }

                is AppViewModel.ConnectionState.ConnectionError -> {
                    StatusMessage(R.string.status_connection_error, connectionState.node)
                }

                is AppViewModel.ConnectionState.ServerNotReachable -> {
                    StatusMessage(R.string.status_server_not_reachable, null)
                }

                is AppViewModel.ConnectionState.ConnectedToServer -> {
                    if (players.isEmpty()) {
                        StatusMessage(R.string.status_no_players, connectionState.node)
                    } else {
                        ScalingLazyColumn {
                            items(players) {
                                val isActive = it.playerId == activePlayerId &&
                                    it.playState == PlayerInfo.PlayState.Playing
                                PlayerRow(it, isActive, onPlayerSelected)
                            }
                            item {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    ActionRow(
                                        titleResId = R.string.action_stop_players,
                                        icon = Icons.Filled.Stop,
                                        onClick = onStopPlayers
                                    )
                                    ActionRow(
                                        titleResId = R.string.action_turn_off_players,
                                        icon = Icons.Filled.SettingsPower,
                                        onClick = onTurnOffPlayers
                                    )
                                    ActionRow(
                                        titleResId = R.string.action_disconnect,
                                        icon = Icons.Filled.Close,
                                        onClick = onDisconnect
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressSpinner() {
    CircularProgressIndicator(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    )
}

@Composable
fun BoxScope.StatusMessage(textResId: Int, node: Node?) {
    val scrollState = rememberScrollState()
    Text(
        text = stringResource(textResId, node?.displayName ?: ""),
        modifier = Modifier
            .scrollable(
                state = scrollState,
                orientation = Orientation.Vertical
            )
            .align(Alignment.Center)
            .padding(16.dp)
    )
}

@Composable
fun PlayerRow(playerInfo: PlayerInfo, isActive: Boolean, onClick: (PlayerInfo) -> Unit) {
    val (stateStringId, stateIcon) = if (!playerInfo.isPowered) {
        Pair(R.string.player_state_off, Icons.Filled.PowerOff)
    } else {
        when (playerInfo.playState) {
            PlayerInfo.PlayState.Playing -> Pair(
                R.string.player_state_playing,
                Icons.Filled.PlayArrow
            )
            PlayerInfo.PlayState.Paused -> Pair(R.string.player_state_paused, Icons.Filled.Pause)
            PlayerInfo.PlayState.Stopped -> Pair(R.string.player_state_stopped, Icons.Filled.Stop)
        }
    }
    Chip(
        onClick = { onClick(playerInfo) },
        label = { Text(text = playerInfo.name) },
        secondaryLabel = {
            Icon(
                imageVector = stateIcon,
                contentDescription = stringResource(stateStringId),
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 4.dp)
                    .align(Alignment.CenterVertically)
            )
            Text(
                text = stringResource(stateStringId),
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        },
        colors = if (isActive) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ActionRow(titleResId: Int, icon: ImageVector, onClick: () -> Unit) {
    Chip(
        label = { Text(stringResource(titleResId)) },
        icon = { Icon(imageVector = icon, contentDescription = stringResource(id = titleResId)) },
        colors = ChipDefaults.secondaryChipColors(),
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    val node = object : Node {
        override fun getDisplayName() = "My Phone"
        override fun getId() = "123456"
        override fun isNearby() = true
    }
    val players = listOf(
        PlayerInfo("Player 1", "abc", isPowered = true, PlayerInfo.PlayState.Paused),
        PlayerInfo("Player 2", "def", isPowered = true, PlayerInfo.PlayState.Playing),
        PlayerInfo("Player 3", "ghi", isPowered = true, PlayerInfo.PlayState.Stopped),
        PlayerInfo("Player 4", "jkl", isPowered = false, PlayerInfo.PlayState.Stopped)
    )
    AppUi(
        connectionState = AppViewModel.ConnectionState.ConnectedToServer(node),
        players = players,
        activePlayerId = "def",
        onPlayerSelected = {},
        onStopPlayers = {},
        onTurnOffPlayers = {},
        onDisconnect = {}
    )
}
