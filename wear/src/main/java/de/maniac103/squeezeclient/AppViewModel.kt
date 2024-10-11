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

import androidx.lifecycle.ViewModel
import com.google.android.gms.wearable.Node
import de.maniac103.squeezeclient.wearapi.ListPlayersRequest
import de.maniac103.squeezeclient.wearapi.PlayerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        class ConnectedToDevice(val node: Node) : ConnectionState()
        class ConnectedToServer(val node: Node) : ConnectionState()
        data object NoDeviceFound : ConnectionState()
        class DeviceAppIncompatible(val node: Node) : ConnectionState()
        class ConnectionError(val node: Node) : ConnectionState()
        data object ServerNotReachable : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _players = MutableStateFlow<List<PlayerInfo>>(emptyList())
    private val _activePlayerId = MutableStateFlow("")

    val connectionState = _connectionState.asStateFlow()
    val players = _players.asStateFlow()
    val activePlayerId = _activePlayerId.asStateFlow()

    fun reset() {
        _connectionState.value = ConnectionState.Disconnected
        _players.value = emptyList()
        _activePlayerId.value = ""
    }

    fun updatePhoneNode(node: Node?, compatible: Boolean) {
        _connectionState.value = when {
            node == null -> ConnectionState.NoDeviceFound
            !compatible -> ConnectionState.DeviceAppIncompatible(node)
            else -> ConnectionState.ConnectedToDevice(node)
        }
    }

    fun updateFromResponse(response: ListPlayersRequest.Response?) {
        val node = (_connectionState.value as? ConnectionState.ConnectedToServer)?.node
            ?: (_connectionState.value as? ConnectionState.ConnectedToDevice)?.node
            ?: return
        _connectionState.value = when {
            response == null -> ConnectionState.ConnectionError(node)
            !response.isConnected -> ConnectionState.ServerNotReachable
            else -> ConnectionState.ConnectedToServer(node)
        }
        if (response != null) {
            _players.value = response.players
            _activePlayerId.value = response.activePlayerId.orEmpty()
        }
    }
}
