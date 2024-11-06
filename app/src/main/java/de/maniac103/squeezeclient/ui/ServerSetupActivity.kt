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

package de.maniac103.squeezeclient.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.ActivityServerSetupBinding
import de.maniac103.squeezeclient.extfuncs.addContentSystemBarAndCutoutInsetsListener
import de.maniac103.squeezeclient.extfuncs.addSystemBarAndCutoutInsetsListener
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.putServerConfig
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.ServerConfiguration
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.internal.and

class ServerSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityServerSetupBinding
    private var progressUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityServerSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.getBooleanExtra(EXTRA_ALLOW_BACK, false)) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24dp)
            binding.toolbar.setNavigationOnClickListener { finish() }
        }
        binding.appbarContainer.addSystemBarAndCutoutInsetsListener()
        binding.content.addContentSystemBarAndCutoutInsetsListener()
        binding.serverAddress.doAfterTextChanged { validateInput() }
        binding.username.doAfterTextChanged { validateInput() }
        binding.password.doAfterTextChanged { validateInput() }

        binding.connectButton.setOnClickListener {
            prefs.edit {
                val address = binding.serverAddress.text?.toString()
                    ?: return@edit
                val serverName = binding.discoveredServers.text
                    ?.let {
                        val name = it.toString()
                        if (name == getString(R.string.server_enter_manually)) null else name
                    }
                    ?: address
                putServerConfig(
                    ServerConfiguration(
                        serverName,
                        address,
                        binding.username.text?.toString(),
                        binding.password.text?.toString()
                    )
                )
                val intent = Intent(this@ServerSetupActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                finish()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val discoveryResultsChannel = Channel<ServerDiscoveryResult>()
                withContext(Dispatchers.IO) {
                    DatagramSocket()
                }.use { socket ->
                    socket.soTimeout = DISCOVERY_TIMEOUT.inWholeMilliseconds.toInt()
                    updateUiForDiscoveryStart()
                    launch {
                        listenForDiscoveryResults(socket, discoveryResultsChannel)
                    }
                    if (sendDiscoverRequest(socket)) {
                        val results = discoveryResultsChannel.consumeAsFlow().toList()
                        updateUiForDiscoveryResults(results)
                    } else {
                        updateUiForDiscoveryResults(emptyList())
                    }
                }
            }
        }
    }

    private fun updateUiForDiscoveryStart() {
        binding.discoveredServersWrapper.hint = getString(R.string.server_scanning)
        binding.discoveredServersWrapper.isEnabled = false
        binding.scanProgress.isVisible = true
        progressUpdateJob = lifecycleScope.launch {
            val stepDelay = DISCOVERY_TIMEOUT.div(100)
            (0 until 100).forEach {
                delay(stepDelay)
                binding.scanProgress.progress = it
            }
        }
    }

    private fun updateUiForDiscoveryResults(results: List<ServerDiscoveryResult>) {
        binding.discoveredServersWrapper.hint = getString(R.string.server_choose_hint)
        binding.discoveredServersWrapper.isEnabled = true
        binding.scanProgress.isVisible = false

        val currentConfig = prefs.serverConfig
        val adapterEntries = results.toMutableList().apply {
            add(ServerDiscoveryResult(getString(R.string.server_enter_manually), "", null))
        }
        binding.discoveredServers.setSimpleItems(
            adapterEntries.map { it.serverName }.toTypedArray()
        )
        binding.discoveredServers.doAfterTextChanged {
            val selected = it?.toString()?.let { name ->
                adapterEntries.find { entry -> entry.serverName == name }
            } ?: return@doAfterTextChanged
            binding.serverAddressWrapper.isEnabled = selected.hostName.isEmpty()
            binding.serverAddress.setText(
                when {
                    selected.hostName.isEmpty() -> currentConfig?.hostnameAndPort
                    selected.port == null -> selected.hostName
                    else -> "${selected.hostName}:${selected.port}"
                }
            )
        }
        binding.discoveredServers.setText(adapterEntries[0].serverName)
        binding.username.setText(currentConfig?.username)
        binding.password.setText(currentConfig?.password)
    }

    private fun validateInput() {
        val addressValue = binding.serverAddress.text?.toString()
        val userValue = binding.username.text?.toString()
        val passwordValue = binding.password.text?.toString()

        val (addressEnabled, addressError) = when {
            addressValue.isNullOrEmpty() -> Pair(false, null)
            addressValue.toString().let { "http://$it" }.toHttpUrlOrNull() != null ->
                Pair(true, null)
            else -> Pair(false, getString(R.string.server_address_error, addressValue))
        }
        val (credsEnabled, credsError) = when {
            !userValue.isNullOrEmpty() && passwordValue.isNullOrEmpty() ->
                Pair(false, getString(R.string.server_creds_error))
            else -> Pair(true, null)
        }

        binding.connectButton.isEnabled = addressEnabled && credsEnabled
        binding.serverAddressWrapper.error = addressError
        binding.passwordWrapper.error = credsError
    }

    private suspend fun sendDiscoverRequest(socket: DatagramSocket): Boolean {
        val requestData = "eIPAD\u0000NAME\u0000JSON\u0000".toByteArray(Charsets.US_ASCII)
        return withContext(Dispatchers.IO) {
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val discoveryPacket = DatagramPacket(
                requestData,
                requestData.size,
                broadcastAddress,
                DISCOVERY_PORT
            )
            try {
                socket.send(discoveryPacket)
                true
            } catch (e: IOException) {
                Log.d(TAG, "could not send discovery request", e)
                false
            }
        }
    }

    private suspend fun listenForDiscoveryResults(
        socket: DatagramSocket,
        resultChannel: Channel<ServerDiscoveryResult>
    ) = withContext(Dispatchers.IO) {
        val buf = ByteArray(512)
        val responsePacket = DatagramPacket(buf, buf.size)
        while (isActive) {
            try {
                socket.receive(responsePacket)
            } catch (e: IOException) {
                Log.d(TAG, "Receiving discovery packet failed", e)
                break
            }
            if (buf[0] == 'E'.code.toByte()) {
                val keyValuePairs = parseDiscoveryResult(responsePacket.data, responsePacket.length)
                val serverName = keyValuePairs["NAME"]
                val hostName = responsePacket.address.hostAddress
                if (serverName != null && hostName != null) {
                    val port = keyValuePairs["JSON"]?.toInt()
                    resultChannel.send(ServerDiscoveryResult(serverName, hostName, port))
                }
            }
        }
        resultChannel.close()
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

            // Read the length, and skip over it.& 0xff to it is an unsigned byte
            val valueLength = data[position++] and 0xff

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

    data class ServerDiscoveryResult(
        val serverName: String,
        val hostName: String,
        val port: Int?
    ) {
        override fun toString() = serverName
    }

    companion object {
        private const val TAG = "ServerSetupActivity"
        private const val DISCOVERY_PORT = 3483
        private val DISCOVERY_TIMEOUT = 2.seconds

        private const val EXTRA_ALLOW_BACK = "allowBack"

        fun createIntent(context: Context, allowBack: Boolean): Intent {
            return Intent(context, ServerSetupActivity::class.java).apply {
                putExtra(EXTRA_ALLOW_BACK, allowBack)
            }
        }
    }
}
