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

data class AllPlayerActionRequest(val action: Action) {
    enum class Action {
        StopPlayback,
        TurnOff
    }

    suspend fun send(messageClient: MessageClient, nodeId: String): Boolean {
        val data = DataMap().apply {
            putInt("action", action.ordinal)
        }
        val response = messageClient.sendRequest(nodeId, PATH, data.toByteArray()).await()
        return response[0].toInt() != 0
    }

    companion object {
        const val PATH = "/wear/playeraction/all"
    }
}

fun ByteArray.asAllPlayerActionRequest(): AllPlayerActionRequest {
    val dataMap = DataMap.fromByteArray(this)
    val action = AllPlayerActionRequest.Action.entries[dataMap.getInt("action")]
    return AllPlayerActionRequest(action)
}
