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

package de.maniac103.squeezeclient.service.mediasession

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.lastSelectedPlayer
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.service.NotificationIds
import de.maniac103.squeezeclient.ui.MainActivity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalTime::class)
class MediaService :
    MediaSessionService(),
    LifecycleOwner,
    MediaSession.Callback {
    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle get() = dispatcher.lifecycle
    private lateinit var player: SqueezeboxMediaPlayer
    private lateinit var mediaSession: MediaSession
    private var lastDisconnectionTime = Clock.System.now()
    private var delayedShutdownJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        player = SqueezeboxMediaPlayer(applicationContext, connectionHelper, lifecycle)

        val channelInfo = NotificationIds.CHANNEL_MEDIA_CONTROL
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NotificationIds.MEDIA_CONTTROL_SERVICE)
                .setChannelId(channelInfo.id)
                .setChannelName(channelInfo.nameResId)
                .build()
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.state.collectLatest { status ->
                    when (status) {
                        is ConnectionState.Disconnected -> handleDisconnection()
                        is ConnectionState.Connecting -> {}
                        is ConnectionState.Connected -> handleConnection(status)
                        is ConnectionState.ConnectionFailure -> handleDisconnection()
                    }
                }
            }
        }

        player.currentPlayer = prefs.lastSelectedPlayer

        val powerButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(R.string.notif_action_player_power))
            .setCustomIconResId(R.drawable.ic_power_24dp)
            .setSessionCommand(SessionCommand(SESSION_ACTION_POWER, bundleOf()))
            .build()
        val disconnectButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(R.string.notif_action_disconnect))
            .setCustomIconResId(R.drawable.ic_disconnect_24dp)
            .setSessionCommand(SessionCommand(SESSION_ACTION_DISCONNECT, bundleOf()))
            .build()

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(httpClient)
        )
        val bitmapLoader = DataSourceBitmapLoader.Builder(this)
            .setDataSourceFactory(dataSourceFactory)
            .build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(this)
            .setCustomLayout(listOf(powerButton, disconnectButton))
            .setSessionActivity(activityIntent)
            .setBitmapLoader(CacheBitmapLoader(bitmapLoader))
            .build()
        addSession(mediaSession)
    }

    override fun onBind(intent: Intent?): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        if (intent?.action == ACTION_START_WITH_PLAYER) {
            val playerId = requireNotNull(
                IntentCompat.getParcelableExtra(intent, "playerId", PlayerId::class.java)
            )
            val forcePlayerChange = intent.getBooleanExtra("forcePlayerChange", true)
            if (player.currentPlayer == null || forcePlayerChange) {
                player.currentPlayer = playerId
            }
            return START_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.playbackState != Player.STATE_READY && !player.playWhenReady) {
            stopSelf()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand(SESSION_ACTION_POWER, Bundle.EMPTY))
            .add(SessionCommand(SESSION_ACTION_DISCONNECT, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(sessionCommands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> = lifecycleScope.future {
        val result = when (customCommand.customAction) {
            SESSION_ACTION_DISCONNECT -> {
                stopSelf()
                SessionResult.RESULT_SUCCESS
            }

            SESSION_ACTION_POWER -> {
                player.currentPlayer?.let { connectionHelper.togglePower(it) }
                SessionResult.RESULT_SUCCESS
            }

            else -> SessionResult.RESULT_ERROR_NOT_SUPPORTED
        }
        SessionResult(result)
    }

    private fun handleConnection(status: ConnectionState.Connected) {
        // Update connection status first, because currentPlayer checked below
        // is updated on status changes
        player.isConnectedToServer = true
        player.currentPlayer?.let { playerId ->
            if (status.players.any { it.id == playerId }) {
                delayedShutdownJob?.cancel()
                delayedShutdownJob = null
            } else if (delayedShutdownJob == null) {
                // The player disappeared from the server's player list. That happens briefly
                // when a player (e.g. this device's local player on a mobile connection)
                // reconnects, so keep the media session for a while before giving it up.
                delayedShutdownJob = lifecycleScope.launch {
                    delay(30.seconds)
                    stopSelf()
                }
            }
        }
    }

    private fun handleDisconnection() {
        if (player.isConnectedToServer) {
            lastDisconnectionTime = Clock.System.now()
            player.isConnectedToServer = false
        }
        // Try reconnecting for some amount of time to handle short interruptions. If we can't
        // connect for longer amounts of time (using 15 minutes as an arbitrarily chosen timeout),
        // stop retrying and shut down the service (which in turn removes the media notification)
        val retryTime = Clock.System.now() - lastDisconnectionTime
        if (retryTime < 15.minutes) {
            connectionHelper.connect()
        } else {
            stopSelf()
        }
    }

    companion object {
        private val ACTION_START_WITH_PLAYER = MediaService::class.java.name + ".startWithPlayer"

        private const val SESSION_ACTION_POWER = "power"
        private const val SESSION_ACTION_DISCONNECT = "disconnect"

        fun start(context: Context, playerId: PlayerId, forcePlayerChange: Boolean) {
            val intent = Intent(context, MediaService::class.java).apply {
                action = ACTION_START_WITH_PLAYER
                putExtra("playerId", playerId)
                putExtra("forcePlayerChange", forcePlayerChange)
            }
            context.startForegroundService(intent)
        }
    }
}
