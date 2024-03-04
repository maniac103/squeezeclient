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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import de.maniac103.squeezeclient.wearapi.AllPlayerActionRequest
import de.maniac103.squeezeclient.wearapi.ListPlayersRequest
import de.maniac103.squeezeclient.wearapi.PlayerInfo
import de.maniac103.squeezeclient.wearapi.StartMediaServiceRequest
import de.maniac103.squeezeclient.wearapi.StopMediaServiceRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val viewModel: AppViewModel by viewModels()
    private val connectedNode get() =
        (viewModel.connectionState.value as? AppViewModel.ConnectionState.ConnectedToServer)?.node

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            AppUi(
                viewModel,
                onPlayerSelected = { info -> handlePlayerSelected(info) },
                onStopPlayers = { handlePlayersAction(AllPlayerActionRequest.Action.StopPlayback) },
                onTurnOffPlayers = { handlePlayersAction(AllPlayerActionRequest.Action.TurnOff) },
                onDisconnect = { handleDisconnect() }
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reset()

                val (node, compatible) = connectToPhone()
                viewModel.updatePhoneNode(node, compatible)

                if (node != null && compatible) {
                    val response = requestPlayerList(node)
                    viewModel.updateFromResponse(response)
                    val activePlayer = response?.let { resp ->
                        resp.players.find { it.playerId == resp.activePlayerId }
                    }
                    if (activePlayer?.playState == PlayerInfo.PlayState.Playing) {
                        val mediaRequest = StartMediaServiceRequest(activePlayer.playerId)
                        mediaRequest.send(messageClient, node.id)
                    }
                    while (isActive) {
                        delay(5.seconds)
                        viewModel.updateFromResponse(requestPlayerList(node))
                    }
                }
            }
        }
    }

    private fun handlePlayerSelected(info: PlayerInfo) {
        val node = connectedNode ?: return
        val mediaRequest = StartMediaServiceRequest(info.playerId)
        lifecycleScope.launch {
            if (mediaRequest.send(messageClient, node.id)) {
                viewModel.updateFromResponse(requestPlayerList(node))
            }
        }
    }

    private fun handlePlayersAction(action: AllPlayerActionRequest.Action) {
        val node = connectedNode ?: return
        val request = AllPlayerActionRequest(action)
        lifecycleScope.launch {
            if (request.send(messageClient, node.id)) {
                viewModel.updateFromResponse(requestPlayerList(node))
            }
        }
    }

    private fun handleDisconnect() {
        val node = connectedNode ?: return
        val request = StopMediaServiceRequest()
        lifecycleScope.launch {
            request.send(messageClient, node.id)
            finish()
        }
    }

    private suspend fun connectToPhone(): Pair<Node?, Boolean> {
        val caps = capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
            .await()
        val phoneAppCapInfo = caps[
            getString(de.maniac103.squeezeclient.wearapi.R.string.phone_app_capability)
        ]
        val phoneAppNode = phoneAppCapInfo?.nodes?.firstOrNull()
            ?: return Pair(null, false)

        val versionCapInfo = caps[
            getString(de.maniac103.squeezeclient.wearapi.R.string.wearapi_version_capability)
        ]
        val compatible = versionCapInfo?.nodes?.any { it.id == phoneAppNode.id } == true
        return Pair(phoneAppNode, compatible)
    }

    private suspend fun requestPlayerList(node: Node): ListPlayersRequest.Response? {
        val request = ListPlayersRequest()
        return withTimeoutOrNull(10.seconds) {
            request.send(messageClient, node.id)
        }
    }
}