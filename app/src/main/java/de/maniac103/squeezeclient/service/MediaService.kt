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

package de.maniac103.squeezeclient.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.ConnectionState
import de.maniac103.squeezeclient.cometd.request.PlaybackButtonRequest
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.lastSelectedPlayer
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.volumeStepSize
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.ui.MainActivity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalTime::class)
class MediaService :
    MediaSessionService(),
    LifecycleOwner,
    MediaSession.Callback {
    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle get() = dispatcher.lifecycle
    private lateinit var player: SqueezeboxPlayer
    private lateinit var mediaSession: MediaSession
    private var lastDisconnectionTime = Clock.System.now()
    private var delayedShutdownJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        player = SqueezeboxPlayer(applicationContext, connectionHelper, lifecycle)

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
    ): ConnectionResult {
        val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand(SESSION_ACTION_POWER, Bundle.EMPTY))
            .add(SessionCommand(SESSION_ACTION_DISCONNECT, Bundle.EMPTY))
            .build()
        return ConnectionResult.AcceptedResultBuilder(session, controller)
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

    @OptIn(UnstableApi::class)
    class SqueezeboxPlayer(
        private val appContext: Context,
        private val connectionHelper: ConnectionHelper,
        private val lifecycle: Lifecycle
    ) : SimpleBasePlayer(Looper.getMainLooper()),
        CoroutineScope by lifecycle.coroutineScope {
        var currentPlayer: PlayerId? = null
            set(value) {
                if (field != value) {
                    field = value
                    updatePlayer(value)
                }
            }
        var isConnectedToServer: Boolean = true
            set(value) {
                if (field != value) {
                    field = value
                    if (!value) {
                        disconnectedTime = SystemClock.elapsedRealtime()
                    }
                    updatePlayer(currentPlayer)
                    invalidateStateCoalesced()
                }
            }
        private var latestStatus: PlayerStatus? = null
        private var latestPlaylist: Playlist? = null
        private var latestPlaylistChange: Instant? = null
        private var pendingPlaylistChange: Instant? = null
        private var lastKnownSong: Playlist.PlaylistItem? = null
        private var lastState: State? = null

        // The song the last reported state refers to, to tell whether a cached state still matches
        // the song a newer player status reports.
        private var lastStateSong: Playlist.PlaylistItem? = null
        private var statusSubscription: Job? = null
        private var stateInvalidationJob: Job? = null
        private var disconnectedTime = 0L

        override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int) = future {
            val playerId = currentPlayer ?: return@future
            connectionHelper.setVolume(playerId, deviceVolume)
        }

        override fun handleIncreaseDeviceVolume(flags: Int) = future {
            val playerId = currentPlayer ?: return@future
            val currentVolume = latestStatus?.currentVolume ?: return@future
            val stepSize = appContext.prefs.volumeStepSize
            connectionHelper.setVolume(playerId, currentVolume + stepSize)
        }

        override fun handleDecreaseDeviceVolume(flags: Int) = future {
            val playerId = currentPlayer ?: return@future
            val currentVolume = latestStatus?.currentVolume ?: return@future
            val stepSize = appContext.prefs.volumeStepSize
            connectionHelper.setVolume(playerId, currentVolume - stepSize)
        }

        override fun handleSetDeviceMuted(muted: Boolean, flags: Int) = future {
            val playerId = currentPlayer ?: return@future
            connectionHelper.setMuteState(playerId, muted)
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean) = future {
            val playerId = currentPlayer ?: return@future
            val newState = when {
                playWhenReady -> PlayerStatus.PlayState.Playing
                else -> PlayerStatus.PlayState.Paused
            }
            connectionHelper.changePlaybackState(playerId, newState)
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int) = future {
            val playerId = currentPlayer ?: return@future
            when (seekCommand) {
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT ->
                    connectionHelper.sendButtonRequest(PlaybackButtonRequest.NextTrack(playerId))

                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS ->
                    connectionHelper.sendButtonRequest(
                        PlaybackButtonRequest.PreviousTrack(playerId)
                    )

                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                    val positionSeconds = ((positionMs + 500) / 1000).toInt()
                    connectionHelper.updatePlaybackPosition(playerId, positionSeconds)
                }

                COMMAND_SEEK_TO_MEDIA_ITEM -> {
                    val positionSeconds = ((positionMs + 500) / 1000).toInt()
                    connectionHelper.advanceToPlaylistPosition(playerId, mediaItemIndex)
                    if (positionSeconds > 0) {
                        connectionHelper.updatePlaybackPosition(playerId, positionSeconds)
                    }
                }

                else -> {}
            }
        }

        override fun handleStop(): ListenableFuture<*> = future {
            val playerId = currentPlayer ?: return@future
            connectionHelper.changePlaybackState(playerId, PlayerStatus.PlayState.Stopped)
        }

        override fun getState(): State {
            val status = latestStatus
                ?: return waitingForPlayerState()
            status.playlist.nowPlaying?.let { lastKnownSong = it }
            // The server reports a sequence of intermediate states when changing tracks (track
            // stopped, stream restarted, ...). Keep reporting the song we last heard about
            // while the server's state is transient - reporting a state without a current item
            // would make connected devices (e.g. car head units) blank or revert their display.
            val currentSong = status.playlist.nowPlaying ?: lastKnownSong
            if (currentSong == null) {
                return State.Builder().setPlaybackState(STATE_IDLE).build()
            }

            val currentSongDurationUs =
                status.currentSongDuration?.toLong(DurationUnit.MICROSECONDS)

            val window = latestPlaylist
            val windowCurrent = latestPlaylistChange == status.playlist.lastChange
            if (!windowCurrent && pendingPlaylistChange != null && currentSong == lastStateSong) {
                // The playlist changed and its new contents are still being fetched. Keep the
                // last known state while the status still reports the same song; if the status is
                // newer than our playlist, its song wins - caching would show the previous track
                // again to connected devices (e.g. car head units) after the new one appeared.
                lastState?.let { return it }
            }

            // Only use the playlist contents if they belong to the revision the status refers
            // to; mixing both would make us report the wrong item.
            val currentPosition = status.playlist.currentPosition - 1
            val windowPosition = if (window != null) currentPosition - window.offset else -1
            val (playlist, currentIndex) = if (windowCurrent && window != null &&
                windowPosition in window.items.indices
            ) {
                val mediaList: List<MediaItemData> = window.items.mapIndexed { index, item ->
                    val builder = if (index == windowPosition) {
                        // Prefer current song from status over playlist item, because the former
                        // may be more up to date (e.g. in case of radio streams)
                        currentSong.toMediaItemDataBuilder(index).apply {
                            currentSongDurationUs?.let { setDurationUs(it) }
                        }
                    } else {
                        item.toMediaItemDataBuilder(index)
                    }
                    builder.build()
                }
                Pair(mediaList, windowPosition)
            } else {
                val builder = currentSong.toMediaItemDataBuilder(0)
                currentSongDurationUs?.let { builder.setDurationUs(it) }
                Pair(listOf(builder.build()), 0)
            }

            val commandsBuilder = Player.Commands.Builder().apply {
                add(COMMAND_GET_CURRENT_MEDIA_ITEM)
                add(COMMAND_GET_METADATA)
                add(COMMAND_GET_TIMELINE)
                add(COMMAND_PLAY_PAUSE)
                if (status.currentSongDuration != null &&
                    status.playbackState != PlayerStatus.PlayState.Stopped
                ) {
                    add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                }
                add(COMMAND_SEEK_TO_MEDIA_ITEM)
                add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                add(COMMAND_SEEK_TO_NEXT)
                add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                add(COMMAND_SEEK_TO_PREVIOUS)
                add(COMMAND_STOP)
                if (status.currentVolume != null) {
                    add(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                    add(COMMAND_GET_DEVICE_VOLUME)
                    add(COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                }
            }

            val playWhenReady = status.playbackState == PlayerStatus.PlayState.Playing
            val playbackState = when {
                // Only report a disconnection as buffering after it lasted a moment; a short
                // connection loss (e.g. while the cometd connection is re-established) must not
                // flap the state reported to connected devices.
                !isConnectedToServer &&
                    (SystemClock.elapsedRealtime() - disconnectedTime) >
                    CONNECTION_LOSS_GRACE_TIME ->
                    STATE_BUFFERING

                !status.powered -> STATE_IDLE

                else -> when (status.playbackState) {
                    PlayerStatus.PlayState.Playing -> STATE_READY
                    PlayerStatus.PlayState.Paused -> STATE_READY
                    PlayerStatus.PlayState.Stopped -> STATE_IDLE
                }
            }

            val builder = State.Builder()
                .setPlaybackState(playbackState)
                .setAvailableCommands(commandsBuilder.build())
                .setContentPositionMs(
                    status.currentPlayPosition?.toLong(DurationUnit.MILLISECONDS) ?: C.TIME_UNSET
                )
                .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .setPlaylist(playlist)
                .setCurrentMediaItemIndex(currentIndex)

            status.currentVolume?.let {
                builder.setDeviceVolume(it)
                builder.setDeviceInfo(
                    DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                        .setMinVolume(0)
                        .setMaxVolume(100)
                        .build()
                )
            }
            status.muted?.let { builder.setIsDeviceMuted(it) }

            return builder.build().also {
                lastState = it
                lastStateSong = currentSong
            }
        }

        /**
         * State to report while nothing is known about the controlled player yet (e.g. right
         * after the service has been started). A buffering state with a placeholder item is
         * reported instead of an empty state, because the media session only becomes visible
         * to the system (and the service only gets into the foreground) if it has a timeline
         * with a playing/buffering item.
         */
        private fun waitingForPlayerState(): State {
            val placeholder = MediaItemData.Builder(0)
                .setMediaItem(MediaItem.Builder().setMediaId("waitingForPlayer").build())
                .build()
            return State.Builder()
                .setPlaybackState(STATE_BUFFERING)
                .setPlayWhenReady(true, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .setPlaylist(listOf(placeholder))
                .setCurrentMediaItemIndex(0)
                .build()
        }

        @kotlin.OptIn(ExperimentalCoroutinesApi::class)
        private fun updatePlayer(playerId: PlayerId?) {
            statusSubscription?.cancel()
            if (playerId == null || !isConnectedToServer) {
                return
            }
            statusSubscription = launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    connectionHelper.playerState(playerId)
                        .flatMapLatest { it.playStatus }
                        .collect { status -> handlePlayerStatus(playerId, status) }
                }
            }
        }

        private fun handlePlayerStatus(playerId: PlayerId, status: PlayerStatus) {
            val previous = latestStatus
            // Status updates can arrive out of order, e.g. when a response to a request sent
            // before a track change is delivered after the server pushed the new state. Applying
            // such an outdated status makes connected devices (e.g. car head units) show the
            // previous track again for a moment, so ignore statuses referring to an older
            // playlist revision than the one we already know about.
            if (previous != null && status.playlist.lastChange < previous.playlist.lastChange) {
                return
            }
            val newChange = status.playlist.lastChange
            if (newChange != latestPlaylistChange && newChange != pendingPlaylistChange) {
                pendingPlaylistChange = newChange
                launch {
                    val playlist = runCatching {
                        connectionHelper.fetchPlaylist(playerId, PagingParams.All)
                    }.getOrNull()
                    // Requests may complete out of order, e.g. when the playlist changes while
                    // a previous fetch is still in flight. Ignore such outdated results.
                    if (pendingPlaylistChange == newChange) {
                        if (playlist != null) {
                            latestPlaylist = playlist
                            latestPlaylistChange = newChange
                        }
                        pendingPlaylistChange = null
                        invalidateStateCoalesced()
                    }
                }
            }
            latestStatus = status
            val playbackStateChanged = previous?.let {
                it.playbackState != status.playbackState || it.muted != status.muted ||
                    it.powered != status.powered || it.currentVolume != status.currentVolume
            } == true
            if (playbackStateChanged) {
                stateInvalidationJob?.cancel()
                invalidateState()
            } else {
                invalidateStateCoalesced()
            }
        }

        /**
         * Coalesces state updates. The server reports a series of intermediate states while
         * changing tracks, and reporting each of them makes connected devices (e.g. car head
         * units) receive contradicting metadata in rapid succession. Only report the state once
         * the server's reporting has settled.
         */
        private fun invalidateStateCoalesced() {
            stateInvalidationJob?.cancel()
            stateInvalidationJob = launch {
                delay(STATE_UPDATE_COALESCE_TIME)
                invalidateState()
            }
        }

        private fun Playlist.PlaylistItem.toMediaItemDataBuilder(
            position: Int
        ): MediaItemData.Builder {
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(extractIconUrl(appContext)?.toUri())
                .build()
            return MediaItemData.Builder(position)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(position.toString())
                        .setMediaMetadata(metadata)
                        .build()
                )
                .setMediaMetadata(metadata)
        }

        companion object {
            // Time to wait for the server's state reporting to settle before reporting a state
            // change to the system (and with it to connected devices such as car head units).
            // The server reports a series of intermediate states while changing tracks, e.g.
            // because it rewrites the playlist on the fly; reporting each of them would make
            // those devices receive contradicting metadata in rapid succession.
            private const val STATE_UPDATE_COALESCE_TIME = 1500L

            // Time a server connection loss is ignored before reporting the player as buffering.
            private const val CONNECTION_LOSS_GRACE_TIME = 3000L
        }
    }
}
