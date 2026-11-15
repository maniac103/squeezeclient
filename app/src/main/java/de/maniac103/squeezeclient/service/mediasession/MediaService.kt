
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

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.NullableType
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.service.NotificationIds
import de.maniac103.squeezeclient.service.getOrCreateLocalPlayerId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject

@OptIn(UnstableApi::class, ExperimentalTime::class)
class MediaService : MediaLibraryService() {
    private val connectionHelper: ConnectionHelper by inject()
    private val preferences: Preferences by inject()
    private val httpClient: OkHttpClient by inject()
    private var lastDisconnectionTime: Instant? = null
    private var playerDisappearanceTime: Instant? = null
    private var delayedShutdownJob: Job? = null

    private lateinit var selectedSession: PlayerSession
    private lateinit var localSession: PlayerSession

    private val selectedPlayerIdFlow = MutableStateFlow<PlayerId?>(null)
    private val isConnectedToServerFlow = MutableStateFlow(true)
    private val localPlayerId by lazy { preferences.getOrCreateLocalPlayerId() }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val channelInfo = NotificationIds.CHANNEL_MEDIA_CONTROL
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationIdProvider { session ->
                    when (session.id) {
                        localSession.mediaSession.id -> NotificationIds.MEDIA_CONTROL_LOCAL
                        else -> NotificationIds.MEDIA_CONTROL_REMOTE
                    }
                }
                .setChannelId(channelInfo.id)
                .setChannelName(channelInfo.nameResId)
                .build()
        )

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(httpClient)
        )
        val bitmapLoader = DataSourceBitmapLoader.Builder(this)
            .setDataSourceFactory(dataSourceFactory)
            .build()

        selectedSession = PlayerSession(
            service = this,
            bitmapLoader = bitmapLoader,
            preferences = preferences,
            connectionHelper = connectionHelper,
            lifecycle = lifecycle,
            currentPlayerFlow = selectedPlayerIdFlow.asStateFlow(),
            connectedToServerFlow = isConnectedToServerFlow.asStateFlow(),
            isLocalPlayer = false,
            onPlaybackStateChanged = this::scheduleShutdownIfNeeded,
            onDisconnectRequested = {
                removeSession(selectedSession.mediaSession)
                if (!isSessionAdded(localSession.mediaSession)) {
                    stopSelf()
                }
            }
        )

        localSession = PlayerSession(
            service = this,
            bitmapLoader = bitmapLoader,
            preferences = preferences,
            connectionHelper = connectionHelper,
            lifecycle = lifecycle,
            currentPlayerFlow = MutableStateFlow(localPlayerId.playerId).asStateFlow(),
            connectedToServerFlow = isConnectedToServerFlow.asStateFlow(),
            isLocalPlayer = true,
            onPlaybackStateChanged = this::scheduleShutdownIfNeeded,
            onDisconnectRequested = {}
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.lastSelectedPlayer.asFlow().collect { playerId ->
                    selectedPlayerIdFlow.value = playerId
                    selectedSession.isRegistered = playerId != localPlayerId.playerId
                }
            }
        }

        lifecycleScope.launch {
            localSession.isRegistered = preferences.localPlayerEnabled.value
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.localPlayerEnabled.asFlow().collect { enabled ->
                    localSession.isRegistered = enabled
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("reason") == true) {
            val reason = StartReason.entries[intent.getIntExtra("reason", 0)]
            val playerId = IntentCompat.getParcelableExtra(intent, "playerId", PlayerId::class.java)
            when (reason) {
                StartReason.ActivityStop ->
                    selectedSession.isRegistered =
                        selectedPlayerIdFlow.value != localPlayerId.playerId

                StartReason.RemotePlayerSwitchRequest if playerId != null -> {
                    selectedPlayerIdFlow.value = playerId
                    selectedSession.isRegistered = playerId != localPlayerId.playerId
                }

                else -> {}
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val localPlaying = isSessionAdded(localSession.mediaSession) && localSession.isPlaying
        val selectedPlaying =
            isSessionAdded(selectedSession.mediaSession) && selectedSession.isPlaying
        if (!localPlaying && !selectedPlaying) {
            stopSelf()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        selectedSession.release()
        localSession.release()
        super.onDestroy()
    }

    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ): ListenableFuture<@NullableType Void?> {
        val addedSessions = listOf(selectedSession, localSession)
            .filter { isSessionAdded(it.mediaSession) }
        val foregroundSession = addedSessions.firstOrNull { it.isPlaying }
            ?: addedSessions.firstOrNull()
        val isSecondarySession =
            addedSessions.size > 1 && session.id != foregroundSession?.mediaSession?.id

        return super.onUpdateNotificationAsync(
            session,
            startInForegroundRequired && !isSecondarySession
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        val requestedId = controllerInfo.connectionHints.getString(CONNECTION_HINT_KEY_SESSION_ID)
        val selected = selectedSession.mediaSession
        val local = localSession.mediaSession

        return when {
            requestedId == local.id -> local
            requestedId == selected.id -> selected
            local.isAutoCompanionController(controllerInfo) -> local
            isSessionAdded(local) && localSession.isPlaying -> local
            isSessionAdded(selected) -> selected
            isSessionAdded(local) -> local
            else -> null
        }
    }

    private fun handleConnection(status: ConnectionState.Connected) {
        // Update connection status first, because currentPlayer checked below
        // is updated on status changes
        isConnectedToServerFlow.value = true
        lastDisconnectionTime = null

        val connectedPlayers = status.players.filter { it.connected }
        val activePlayer = selectedPlayerIdFlow.value
            ?.takeIf { id -> connectedPlayers.any { it.id == id } }

        when {
            activePlayer == null && connectedPlayers.isNotEmpty() ->
                selectedPlayerIdFlow.value = connectedPlayers[0].id

            activePlayer != null && connectedPlayers.any { it.id == activePlayer } ->
                playerDisappearanceTime = null

            activePlayer != null && playerDisappearanceTime == null ->
                playerDisappearanceTime = Clock.System.now()
        }

        scheduleShutdownIfNeeded()
    }

    private fun handleDisconnection() {
        if (isConnectedToServerFlow.value) {
            lastDisconnectionTime = Clock.System.now()
            isConnectedToServerFlow.value = false
        }
        // Try reconnecting for some amount of time to handle short interruptions. If we can't
        // connect for longer amounts of time, stop retrying and shut down the service
        // (which in turn removes the media notification)
        connectionHelper.connect()
        scheduleShutdownIfNeeded()
    }

    private fun scheduleShutdownIfNeeded() {
        delayedShutdownJob?.cancel()
        delayedShutdownJob = null

        val shutdownDelayForDisappearedPlayer = playerDisappearanceTime
            ?.let { PLAYER_DISAPPEAR_SHUTDOWN_DELAY - (Clock.System.now() - it) }
        val shutdownDelayForStoppedSelectedPlayer = selectedSession.lastPlayStopTime
            ?.takeIf { isSessionAdded(selectedSession.mediaSession) }
            ?.let { PLAYER_STOP_SHUTDOWN_DELAY - (Clock.System.now() - it) }
        val shutdownDelayForStoppedLocalPlayer = localSession.lastPlayStopTime
            ?.takeIf { isSessionAdded(localSession.mediaSession) }
            ?.let { PLAYER_STOP_SHUTDOWN_DELAY - (Clock.System.now() - it) }
        val shutdownDelayForDisconnection = lastDisconnectionTime
            ?.let { DISCONNECT_SHUTDOWN_DELAY - (Clock.System.now() - it) }

        val shutdownDelays = listOfNotNull(
            shutdownDelayForDisappearedPlayer,
            shutdownDelayForStoppedSelectedPlayer,
            shutdownDelayForStoppedLocalPlayer,
            shutdownDelayForDisconnection
        )

        if (shutdownDelays.isNotEmpty()) {
            delayedShutdownJob = lifecycleScope.launch {
                delay(shutdownDelays.max())
                stopSelf()
            }
        }
    }

    enum class StartReason {
        ActivityStop,
        LocalPlayerStart,
        RemotePlayerSwitchRequest
    }

    companion object {
        private val DISCONNECT_SHUTDOWN_DELAY = 10.minutes
        private val PLAYER_STOP_SHUTDOWN_DELAY = 20.minutes
        private val PLAYER_DISAPPEAR_SHUTDOWN_DELAY = 30.seconds

        fun start(context: Context, reason: StartReason, playerId: PlayerId? = null) {
            val intent = Intent(context, MediaService::class.java).apply {
                putExtra("reason", reason.ordinal)
                putExtra("playerId", playerId)
            }
            context.startForegroundService(intent)
        }
    }
}
