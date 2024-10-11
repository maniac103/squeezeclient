/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.wearapi

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.tasks.await

class ListPlayersRequest {
    suspend fun send(messageClient: MessageClient, nodeId: String): Response {
        val response = messageClient.sendRequest(nodeId, PATH, null).await()
        return response.asConnectAndListPlayersResponse()
    }

    data class Response(
        val isConnected: Boolean,
        val players: List<PlayerInfo>,
        val activePlayerId: String?
    ) {
        fun toByteArray(): ByteArray {
            val data = DataMap().apply {
                putBoolean("connected", isConnected)
                putDataMapArrayList("players", ArrayList(players.map { it.toDataMap() }))
                activePlayerId?.let { putString("activeplayer", it) }
            }
            return data.toByteArray()
        }
    }

    private fun ByteArray.asConnectAndListPlayersResponse(): Response {
        val dataMap = DataMap.fromByteArray(this)
        val connected = dataMap.getBoolean("connected")
        val playerInfos = dataMap.getDataMapArrayList("players")?.let {
            it.map { dm -> dm.asPlayerInfo() }
        }
        val activePlayerId = dataMap.getString("activeplayer")
        if (playerInfos == null) throw IllegalArgumentException()
        return Response(connected, playerInfos, activePlayerId)
    }

    companion object {
        const val PATH = "/wear/players"
    }
}
