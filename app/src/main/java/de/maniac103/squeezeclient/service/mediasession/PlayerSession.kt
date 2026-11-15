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
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.MainActivity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@UnstableApi
class PlayerSession(
    private val service: MediaLibraryService,
    bitmapLoader: BitmapLoader,
    private val preferences: Preferences,
    private val connectionHelper: ConnectionHelper,
    private val lifecycle: Lifecycle,
    private val currentPlayerFlow: StateFlow<PlayerId?>,
    private val connectedToServerFlow: StateFlow<Boolean>,
    private val isLocalPlayer: Boolean,
    private val onPlaybackStateChanged: () -> Unit,
    private val onDisconnectRequested: () -> Unit
) : Player.Listener,
    MediaLibrarySession.Callback,
    CoroutineScope by lifecycle.coroutineScope {
    val isPlaying get() = player.isPlaying
    var lastPlayStopTime: Instant? = null
        private set

    var isRegistered: Boolean
        get() = service.isSessionAdded(mediaSession)
        set(value) {
            if (value) {
                service.addSession(mediaSession)
            } else if (service.isSessionAdded(mediaSession)) {
                service.removeSession(mediaSession)
            }
        }

    private val player = SqueezeboxMediaPlayer(
        preferences = preferences,
        connectionHelper = connectionHelper,
        lifecycle = lifecycle,
        currentPlayerFlow = currentPlayerFlow,
        connectedToServerFlow = connectedToServerFlow,
        isLocalPlayer = isLocalPlayer
    ).apply {
        addListener(this@PlayerSession)
    }

    private val connectedPlayerFlow = combine(
        currentPlayerFlow,
        connectedToServerFlow
    ) { playerId, connected -> playerId?.takeIf { connected } }
        .stateIn(this, SharingStarted.Eagerly, null)

    val mediaSession: MediaLibrarySession
    private val libraryTree: LibraryTree
    private val subscriptions: LibrarySubscriptions

    private val powerCommand = SessionCommand(SESSION_ACTION_POWER, Bundle.EMPTY)
    private val disconnectCommand = SessionCommand(SESSION_ACTION_DISCONNECT, Bundle.EMPTY)

    init {
        val customButtons = if (isLocalPlayer) {
            emptyList()
        } else {
            val powerButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(service.getString(R.string.notif_action_player_power))
                .setCustomIconResId(R.drawable.ic_power_24dp)
                .setSessionCommand(powerCommand)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
            val disconnectButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(service.getString(R.string.notif_action_disconnect))
                .setCustomIconResId(R.drawable.ic_disconnect_24dp)
                .setSessionCommand(disconnectCommand)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()

            listOf(powerButton, disconnectButton)
        }

        val activityIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).setAction(Intent.ACTION_MAIN),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(service, player, this)
            .setId(if (isLocalPlayer) "local" else "remote")
            .setMediaButtonPreferences(customButtons)
            .setSessionActivity(activityIntent)
            .setBitmapLoader(bitmapLoader)
            .build()

        subscriptions = LibrarySubscriptions(mediaSession)
        libraryTree = LibraryTree(service, connectionHelper, preferences)

        launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectedPlayerFlow.collect { subscriptions.notifyChange() }
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun release() {
        player.release()
        mediaSession.release()
    }

    @OptIn(UnstableApi::class)
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) =
        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(buildSessionCommands())
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> = lifecycle.coroutineScope.future {
        val result = when (customCommand.customAction) {
            SESSION_ACTION_DISCONNECT -> {
                onDisconnectRequested()
                SessionResult.RESULT_SUCCESS
            }

            SESSION_ACTION_POWER -> {
                connectedPlayerFlow.value?.let {
                    connectionHelper.togglePower(it)
                }
                SessionResult.RESULT_SUCCESS
            }

            else -> SessionResult.RESULT_ERROR_NOT_SUPPORTED
        }
        SessionResult(result)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        handlePlaybackStateChange()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        handlePlaybackStateChange()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ) = future {
        if (preferences.serverConfig.value == null) {
            LibraryResult.ofError(SessionError.ERROR_SESSION_SETUP_REQUIRED)
        } else {
            LibraryResult.ofItem(libraryTree.rootItem(), params)
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ) = future {
        libraryTree.getChildren(connectedPlayerFlow.value, parentId, page, pageSize)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(libraryTree.getItem(mediaId))

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val result = if (subscriptions.add(browser, parentId)) {
            LibraryResult.ofVoid(params)
        } else {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        return Futures.immediateFuture(result)
    }

    override fun onUnsubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String
    ): ListenableFuture<LibraryResult<Void>> {
        subscriptions.remove(browser, parentId)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        subscriptions.removeAll(controller)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = future {
        val count = libraryTree.searchCount(query)
        session.notifySearchResultChanged(browser, query, count, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ) = future {
        libraryTree.getSearchResult(connectedPlayerFlow.value, query, page, pageSize)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) = future {
        libraryTree.processSetMediaItems(connectedPlayerFlow.value, mediaItems, startIndex)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ) = future {
        libraryTree.processAddMediaItems(connectedPlayerFlow.value, mediaItems)
    }

    private fun handlePlaybackStateChange() {
        if (isPlaying) {
            lastPlayStopTime = null
        } else if (lastPlayStopTime == null) {
            lastPlayStopTime = Clock.System.now()
        }

        onPlaybackStateChanged()
    }

    private fun buildSessionCommands() = if (isLocalPlayer) {
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
    } else {
        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .add(powerCommand)
            .add(disconnectCommand)
            .build()
    }

    companion object {
        private const val SESSION_ACTION_POWER = "power"
        private const val SESSION_ACTION_DISCONNECT = "disconnect"
    }
}
