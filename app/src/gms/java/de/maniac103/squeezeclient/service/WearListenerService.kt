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

package de.maniac103.squeezeclient.service

import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.WearableListenerService
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.lastSelectedPlayer
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.putLastSelectedPlayer
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.wearapi.AllPlayerActionRequest
import de.maniac103.squeezeclient.wearapi.ListPlayersRequest
import de.maniac103.squeezeclient.wearapi.PlayerInfo
import de.maniac103.squeezeclient.wearapi.StartMediaServiceRequest
import de.maniac103.squeezeclient.wearapi.StopMediaServiceRequest
import de.maniac103.squeezeclient.wearapi.asAllPlayerActionRequest
import de.maniac103.squeezeclient.wearapi.asStartMediaServiceRequest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.asTask
import kotlinx.coroutines.withTimeoutOrNull

class WearListenerService : WearableListenerService(), LifecycleOwner {
    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle get() = dispatcher.lifecycle

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
    }

    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray?>? {
        val deferred = when (path) {
            ListPlayersRequest.PATH -> lifecycleScope.async {
                val response = handleListPlayersRequest()
                response.toByteArray()
            }
            AllPlayerActionRequest.PATH -> lifecycleScope.async {
                val success = handleAllPlayerActionRequest(data.asAllPlayerActionRequest())
                ByteArray(1) { if (success) 1 else 0 }
            }
            StartMediaServiceRequest.PATH -> lifecycleScope.async {
                val success = handleStartMediaService(data.asStartMediaServiceRequest())
                ByteArray(1) { if (success) 1 else 0 }
            }
            StopMediaServiceRequest.PATH -> lifecycleScope.async {
                handleStopMediaService()
                ByteArray(0)
            }
            else -> null
        }
        return deferred?.asTask() ?: super.onRequest(nodeId, path, data)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun handleListPlayersRequest(): ListPlayersRequest.Response {
        connectionHelper.connect()
        val playerInfos = withTimeoutOrNull(PLAYER_STATUS_TIMEOUT) {
            connectionHelper.state
                .mapNotNull { (it as? ConnectionState.Connected)?.players }
                .flatMapMerge { players ->
                    val playerIdAndStatusFlows = players.map { p ->
                        connectionHelper.playerState(p.id).flatMapLatest { state ->
                            combine(flowOf(state.playerId), state.playStatus) { id, ps ->
                                Pair(id, ps)
                            }
                        }
                    }
                    combine(playerIdAndStatusFlows) { it.toList() }
                }
                .map { playerIdsAndStatus ->
                    playerIdsAndStatus.map { (id, ps) ->
                        val infoState = when (ps.playbackState) {
                            PlayerStatus.PlayState.Stopped -> PlayerInfo.PlayState.Stopped
                            PlayerStatus.PlayState.Paused -> PlayerInfo.PlayState.Paused
                            PlayerStatus.PlayState.Playing -> PlayerInfo.PlayState.Playing
                        }
                        PlayerInfo(
                            ps.playerName,
                            id.id,
                            ps.powered,
                            infoState
                        )
                    }
                }
                .first()
        }
        return if (playerInfos == null) {
            ListPlayersRequest.Response(false, emptyList(), null)
        } else {
            val activePlayerId = prefs.lastSelectedPlayer?.id
            ListPlayersRequest.Response(true, playerInfos, activePlayerId)
        }
    }

    private suspend fun handleAllPlayerActionRequest(request: AllPlayerActionRequest): Boolean {
        connectionHelper.connect()
        val result = withTimeoutOrNull(ACTION_TIMEOUT) {
            val players = connectionHelper.state
                .mapNotNull { it as? ConnectionState.Connected }
                .map { it.players }
                .first()
            players.forEach { p ->
                when (request.action) {
                    AllPlayerActionRequest.Action.TurnOff ->
                        connectionHelper.setPowerState(p.id, false)
                    AllPlayerActionRequest.Action.StopPlayback ->
                        connectionHelper.changePlaybackState(p.id, PlayerStatus.PlayState.Stopped)
                }
            }
            true
        }
        return result == true
    }

    private suspend fun handleStartMediaService(request: StartMediaServiceRequest): Boolean {
        val playerId = PlayerId(request.playerId)
        MediaService.start(this, playerId)
        // Make sure the selected player is playing, as otherwise
        // the media notification doesn't appear on Wear
        connectionHelper.connect()
        val result = withTimeoutOrNull(ACTION_TIMEOUT) {
            // Wait for connection to be ready before sending start command
            val connectedState = connectionHelper.state
                .mapNotNull { it as? ConnectionState.Connected }
                .first()
            val playerIsKnown = connectedState.players.any { it.id == playerId }
            if (playerIsKnown) {
                prefs.edit {
                    putLastSelectedPlayer(playerId)
                }
                connectionHelper.changePlaybackState(playerId, PlayerStatus.PlayState.Playing)
            }
            playerIsKnown
        }
        return result == true
    }

    private fun handleStopMediaService() {
        stopService(Intent(this, MediaService::class.java))
    }

    companion object {
        private val PLAYER_STATUS_TIMEOUT = 10.seconds
        private val ACTION_TIMEOUT = 5.seconds
    }
}
