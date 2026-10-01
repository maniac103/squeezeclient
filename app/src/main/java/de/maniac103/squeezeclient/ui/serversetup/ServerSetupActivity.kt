/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
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

package de.maniac103.squeezeclient.ui.serversetup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.putServerConfig
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.service.localplayer.LocalPlaybackService
import de.maniac103.squeezeclient.ui.MainActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

class ServerSetupActivity : AppCompatActivity() {
    private var discoveryScope: CoroutineScope? = null
    private val discoveryResultsFlow = MutableStateFlow<List<ServerDiscoveryResult>?>(null)
    private val discoveryProgressFlow = MutableStateFlow(0F)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val results by discoveryResultsFlow.collectAsState()
                val progress by discoveryProgressFlow.collectAsState()

                ServerSetupScreen(
                    discoveredServers = results,
                    discoveryProgress = progress,
                    currentServerConfig = prefs.serverConfig,
                    onConnectClicked = { config -> connect(config) },
                    onStartDiscovery = { startDiscovery() },
                    onGoBack = if (intent.getBooleanExtra(EXTRA_ALLOW_BACK, false)) {
                        { finish() }
                    } else {
                        null
                    }
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                startDiscovery()
            }
        }
    }

    private fun connect(config: ServerConfiguration) {
        prefs.edit {
            putServerConfig(config)

            connectionHelper.disconnect()

            val playerIntent = Intent(
                this@ServerSetupActivity,
                LocalPlaybackService::class.java
            )
            stopService(playerIntent)

            val intent = Intent(this@ServerSetupActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)

            finish()
        }
    }

    private fun startDiscovery() {
        discoveryScope?.cancel()
        val scope = (CoroutineScope(lifecycleScope.coroutineContext) + Job()).also {
            discoveryScope = it
        }
        scope.launch {
            discoveryResultsFlow.value = null
            discoveryProgressFlow.value = 0F
            scope.launch {
                val stepDelay = DISCOVERY_TIMEOUT.div(100)
                (0 until 100).forEach {
                    delay(stepDelay)
                    discoveryProgressFlow.value = it / 100F
                }
            }

            val results = doDiscovery(scope)
                .toMutableList()
                .apply {
                    add(ServerDiscoveryResult(getString(R.string.server_enter_manually), "", null))
                }
            discoveryResultsFlow.value = results
        }
    }

    private suspend fun doDiscovery(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        DatagramSocket()
    }.use { socket ->
        val resultsFlow = listenForDiscoveryResults(socket)
        scope.launch {
            delay(DISCOVERY_TIMEOUT)
            socket.close()
        }
        sendDiscoverRequest(socket)
            .mapCatching { resultsFlow.toList() }
            .onFailure { Log.d(TAG, "could not send discovery request", it) }
            .getOrElse { emptyList() }
    }

    private suspend fun sendDiscoverRequest(socket: DatagramSocket): Result<Unit> {
        val requestData = "eIPAD\u0000NAME\u0000JSON\u0000".toByteArray(Charsets.US_ASCII)
        return withContext(Dispatchers.IO) {
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val discoveryPacket = DatagramPacket(
                requestData,
                requestData.size,
                broadcastAddress,
                DISCOVERY_PORT
            )
            runCatching {
                socket.send(discoveryPacket)
            }
        }
    }

    private fun listenForDiscoveryResults(socket: DatagramSocket) = callbackFlow {
        while (isActive) {
            val buf = ByteArray(512)
            val responsePacket = DatagramPacket(buf, buf.size)

            try {
                withContext(Dispatchers.IO) {
                    socket.receive(responsePacket)
                }
            } catch (_: SocketException) {
                channel.close()
            }

            if (isActive && buf[0] == 'E'.code.toByte()) {
                val keyValuePairs = parseDiscoveryResult(
                    responsePacket.data,
                    responsePacket.length
                )
                val serverName = keyValuePairs["NAME"]
                val hostName = responsePacket.address.hostAddress
                Log.d(TAG, "Got discovery result from ${responsePacket.address}: $keyValuePairs")
                if (serverName != null && hostName != null) {
                    val port = keyValuePairs["JSON"]?.toInt()
                    send(ServerDiscoveryResult(serverName, hostName, port))
                }
            }
        }
    }

    private fun parseDiscoveryResult(data: ByteArray, packetLength: Int): Map<String, String> {
        val keyValuePairs = mutableMapOf<String, String>()
        var position = 1

        while (position < packetLength) {
            // Check if the buffer is truncated by the server, and bail out if it is.
            if (position + 5 > packetLength) {
                break
            }

            // Extract type and skip over it
            val key = String(data, position, 4)
            position += 4

            // Read the length, and skip over it
            val valueLength = data[position++].toInt()

            // Check if the buffer is truncated by the server, and bail out if it is.
            if (position + valueLength > packetLength) {
                break
            }

            // Extract the value and skip over it.
            keyValuePairs[key] = String(data, position, valueLength)
            position += valueLength
        }

        return keyValuePairs
    }

    data class ServerDiscoveryResult(val serverName: String, val hostName: String, val port: Int?) {
        override fun toString() = serverName
    }

    companion object {
        private const val TAG = "ServerSetupActivity"
        private const val DISCOVERY_PORT = 3483
        private val DISCOVERY_TIMEOUT = 2.seconds

        private const val EXTRA_ALLOW_BACK = "allowBack"

        fun createIntent(context: Context, allowBack: Boolean) =
            Intent(context, ServerSetupActivity::class.java)
                .putExtra(EXTRA_ALLOW_BACK, allowBack)
    }
}
