/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2025 Danny Baumann
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

package de.maniac103.squeezeclient.service.localplayer

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.getOrCreateNotificationChannel
import de.maniac103.squeezeclient.extfuncs.localPlayerName
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.putLocalPlayerName
import de.maniac103.squeezeclient.service.NotificationIds
import de.maniac103.squeezeclient.ui.MainActivity
import de.maniac103.squeezeclient.ui.prefs.SettingsActivity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response

@OptIn(ExperimentalTime::class)
class LocalPlaybackService :
    Service(),
    LifecycleOwner {
    private val dispatcher = ServiceLifecycleDispatcher(this)
    private lateinit var slimproto: SlimprotoSocket
    private lateinit var player: LocalPlayer
    private lateinit var startupTimestamp: Instant
    override val lifecycle get() = dispatcher.lifecycle

    private var slimprotoJob: Job? = null
    private var stateListenerJob: Job? = null
    private val slimprotoStateFlow = MutableStateFlow<SlimprotoState>(SlimprotoState.Disconnected)

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        slimproto = SlimprotoSocket(prefs)
        player = LocalPlayer(
            this,
            onPlaybackStarted = { onPlaybackStarted() },
            onPlaybackEnded = { onPlaybackEnded() },
            onPauseStateChanged = { paused -> onPauseStateChanged(paused) },
            onHeadersReceived = { resp -> onHeadersReceived(resp) },
            onMetadataReceived = { title, artworkUri -> onMetadataReceived(title, artworkUri) }
        )
        startupTimestamp = Clock.System.now()
    }

    override fun onBind(intent: Intent?): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        return null
    }

    @OptIn(FlowPreview::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()

        if (slimprotoJob == null) {
            slimprotoJob = connectAndRunSlimproto().also {
                it.invokeOnCompletion { slimprotoJob = null }
            }
        }

        if (stateListenerJob == null) {
            stateListenerJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    slimprotoStateFlow
                        .debounce(200.milliseconds)
                        .collectLatest { updateForegroundNotification(it) }
                }
            }
        }

        return START_STICKY
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        GlobalScope.launch {
            slimproto.disconnect()
        }
        player.stop()
        super.onDestroy()
    }

    private fun onHeadersReceived(response: Response) = lifecycleScope.launch {
        slimproto.sendResponseReceived(response)
    }

    private fun onMetadataReceived(title: CharSequence, artworkUri: Uri?) = lifecycleScope.launch {
        slimproto.sendMediaMetaData(title, artworkUri)
        if (player.readyForPlayback) {
            slimprotoStateFlow.emit(SlimprotoState.PlayingOrPaused(title, player.paused))
        }
    }

    private fun onPlaybackStarted() = lifecycleScope.launch {
        slimprotoStateFlow.emit(
            SlimprotoState.PlayingOrPaused(player.playingTitle, player.paused)
        )
        sendStatus(SlimprotoSocket.StatusType.Start)
    }

    private fun onPauseStateChanged(paused: Boolean) = lifecycleScope.launch {
        slimprotoStateFlow.emit(
            SlimprotoState.PlayingOrPaused(player.playingTitle, paused)
        )
    }

    private fun onPlaybackEnded() = lifecycleScope.launch {
        slimprotoStateFlow.emit(SlimprotoState.Stopped)
        slimproto.sendDisconnect()
        sendStatus(SlimprotoSocket.StatusType.Ready)
        sendStatus(SlimprotoSocket.StatusType.Underrun)
    }

    private fun connectAndRunSlimproto() = lifecycleScope.launch {
        slimprotoStateFlow.emit(SlimprotoState.Disconnected)
        while (isActive) {
            var connected = false
            // Attempt server connection
            while (isActive && !connected) {
                connected = slimproto.connect()
                if (!connected) {
                    delay(10.seconds)
                }
            }

            // We're connected, send notification and start reading commands
            slimprotoStateFlow.emit(SlimprotoState.Stopped)
            while (isActive && connected) {
                slimproto.readNextCommand()
                    .onSuccess { handleCommand(it) }
                    .onFailure {
                        Log.d(TAG, "Could not read command from socket", it)
                        connected = slimproto.reconnect()
                    }
            }
            slimprotoStateFlow.emit(SlimprotoState.Disconnected)
        }
    }

    @SuppressLint("InlinedApi")
    private fun updateForegroundNotification(state: SlimprotoState) {
        Log.d(TAG, "update notification with state $state")

        val channel = NotificationManagerCompat.from(this)
            .getOrCreateNotificationChannel(resources, NotificationIds.CHANNEL_LOCAL_PLAYBACK)

        val (title, content) = when {
            state is SlimprotoState.Disconnected ->
                Pair(
                    getString(R.string.local_player_notification_title_disconnected),
                    getString(
                        R.string.local_player_notification_content_disconnected,
                        slimproto.host
                    )
                )

            state is SlimprotoState.PlayingOrPaused && !state.paused ->
                Pair(
                    getString(R.string.local_player_notification_title_playing),
                    getString(R.string.local_player_notification_content_playing, state.title)
                )

            else ->
                Pair(
                    getString(R.string.local_player_notification_title_stopped),
                    getString(R.string.local_player_notification_content_stopped)
                )
        }

        val contentIntentStack = arrayOf(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            Intent(this, SettingsActivity::class.java)
        )
        val contentIntent = PendingIntentCompat.getActivities(
            this,
            0,
            contentIntentStack,
            0,
            false
        )
        val notification = NotificationCompat.Builder(this, channel.id)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.ic_logo_notification_24dp)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NotificationIds.LOCAL_PLAYBACK,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun sendStatus(type: SlimprotoSocket.StatusType) {
        val elapsed = Clock.System.now() - startupTimestamp
        slimproto.sendStatus(
            type,
            elapsed,
            player.playbackPosition,
            player.stats.totalBandwidthBytes
        )
    }

    private suspend fun handleCommand(command: SlimprotoSocket.CommandPacket) {
        when (command) {
            is SlimprotoSocket.CommandPacket.AudioEnable -> {}

            is SlimprotoSocket.CommandPacket.AudioGain -> {
                // TODO: digital volume control?
                player.volume = (command.newLeft + command.newRight) / 2
            }

            is SlimprotoSocket.CommandPacket.Continue -> {
                if (player.readyForPlayback) {
                    player.paused = false
                }
            }

            is SlimprotoSocket.CommandPacket.StreamStart -> {
                sendStatus(SlimprotoSocket.StatusType.Connect)
                player.start(command.uri, command.mimeType, command.headers, command.autoStart)
            }

            is SlimprotoSocket.CommandPacket.StreamPause -> {
                player.paused = true
                sendStatus(SlimprotoSocket.StatusType.Pause)
            }

            is SlimprotoSocket.CommandPacket.StreamUnpause -> {
                player.paused = false
                sendStatus(SlimprotoSocket.StatusType.Resume)
            }

            is SlimprotoSocket.CommandPacket.StreamStop -> {
                player.stop()
                sendStatus(SlimprotoSocket.StatusType.Flushed)
            }

            is SlimprotoSocket.CommandPacket.StreamStatus -> {
                sendStatus(SlimprotoSocket.StatusType.Timer(command.timestamp))
            }

            is SlimprotoSocket.CommandPacket.TriggerGetSetting -> {
                if (command.type == SlimprotoSocket.SettingType.PlayerName) {
                    val name = prefs.localPlayerName ?: "SqueezeClient - ${Build.MODEL}"
                    slimproto.sendSetSetting(command.type, name.toByteArray(Charsets.US_ASCII))
                }
            }

            is SlimprotoSocket.CommandPacket.SetSetting -> {
                if (command.type == SlimprotoSocket.SettingType.PlayerName) {
                    val name = String(command.data)
                    Log.d(TAG, "Changing local player name to $name")
                    prefs.edit {
                        putLocalPlayerName(name)
                    }
                }
            }

            else -> {
                Log.d(TAG, "unhandled command $command")
            }
        }
    }

    sealed class SlimprotoState {
        data object Disconnected : SlimprotoState()
        data object Stopped : SlimprotoState()
        data class PlayingOrPaused(val title: CharSequence?, val paused: Boolean) : SlimprotoState()
    }

    companion object {
        private const val TAG = "LocalPlaybackService"
    }
}
