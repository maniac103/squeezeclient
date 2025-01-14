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

package de.maniac103.squeezeclient.cometd

import de.maniac103.squeezeclient.SqueezeClientApplication
import de.maniac103.squeezeclient.cometd.request.ChangePlaybackStateRequest
import de.maniac103.squeezeclient.cometd.request.ClearPlaylistRequest
import de.maniac103.squeezeclient.cometd.request.ExecuteActionRequest
import de.maniac103.squeezeclient.cometd.request.FetchTrackInfoRequest
import de.maniac103.squeezeclient.cometd.request.FetchAlbumInfoRequest
import de.maniac103.squeezeclient.cometd.request.FetchHomeMenuRequest
import de.maniac103.squeezeclient.cometd.request.FetchItemsForActionRequest
import de.maniac103.squeezeclient.cometd.request.FetchSongInfosForDownloadRequest
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.cometd.request.LocalSearchRequest
import de.maniac103.squeezeclient.cometd.request.MenuStatusRequest
import de.maniac103.squeezeclient.cometd.request.MovePlaylistItemRequest
import de.maniac103.squeezeclient.cometd.request.PlaybackButtonRequest
import de.maniac103.squeezeclient.cometd.request.PlayerPowerRequest
import de.maniac103.squeezeclient.cometd.request.PlayerStatusRequest
import de.maniac103.squeezeclient.cometd.request.RadioSearchRequest
import de.maniac103.squeezeclient.cometd.request.RemovePlaylistItemRequest
import de.maniac103.squeezeclient.cometd.request.Request
import de.maniac103.squeezeclient.cometd.request.SavePlaylistRequest
import de.maniac103.squeezeclient.cometd.request.ServerStatusRequest
import de.maniac103.squeezeclient.cometd.request.SetMuteStateRequest
import de.maniac103.squeezeclient.cometd.request.SetPlaybackPositionRequest
import de.maniac103.squeezeclient.cometd.request.SetPlaylistPositionRequest
import de.maniac103.squeezeclient.cometd.request.SetVolumeRequest
import de.maniac103.squeezeclient.cometd.request.SyncPlayersRequest
import de.maniac103.squeezeclient.cometd.request.UnsyncPlayerRequest
import de.maniac103.squeezeclient.cometd.request.UpdateDisplayStatusSubscriptionRequest
import de.maniac103.squeezeclient.cometd.request.UpdatePlayerStatusSubscriptionRequest
import de.maniac103.squeezeclient.cometd.response.AlbumInfoListResponse
import de.maniac103.squeezeclient.cometd.response.DisplayStatusResponse
import de.maniac103.squeezeclient.cometd.response.DownloadSongInfoListResponse
import de.maniac103.squeezeclient.cometd.response.JiveHomeItemListResponse
import de.maniac103.squeezeclient.cometd.response.LocalSearchResultsResponse
import de.maniac103.squeezeclient.cometd.response.PlayerStatusResponse
import de.maniac103.squeezeclient.cometd.response.ServerStatusResponse
import de.maniac103.squeezeclient.cometd.response.SlideshowListResponse
import de.maniac103.squeezeclient.cometd.response.SlimBrowseListResponse
import de.maniac103.squeezeclient.cometd.response.TrackInfoListResponse
import de.maniac103.squeezeclient.cometd.response.parseToJiveHomeMenuItem
import de.maniac103.squeezeclient.extfuncs.extractSlimBrowseAlbumIdForAlbumListResponse
import de.maniac103.squeezeclient.extfuncs.extractSlimBrowseTrackIdForTrackListResponse
import de.maniac103.squeezeclient.extfuncs.fadeInDuration
import de.maniac103.squeezeclient.extfuncs.fetchesAlbumList
import de.maniac103.squeezeclient.extfuncs.fetchesAlbumTrackList
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.DisplayMessage
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.LocalLibrarySearchResultCounts
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ConnectionHelper(private val appContext: SqueezeClientApplication) {
    private val json = appContext.json
    private var client: CometdClient? = null
    private val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state = stateFlow.asStateFlow()

    private val playerStates = mutableMapOf<PlayerId, PlayerState>()
    private var connectionScope: CoroutineScope? = null
    private var pendingDisconnectJob: Job? = null
    private var mediaDirectories = listOf<String>()
    private var nextRequestId = 1

    fun connect() {
        if (connectionScope != null) {
            // already connected
            return
        }
        val prefs = appContext.prefs
        val client = prefs.serverConfig?.let { CometdClient(appContext.httpClient, json, it) }
            ?: return

        this.client = client
        val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()
        scope.launch {
            stateFlow.emit(ConnectionState.Connecting)
            // Establish cometd connection
            val clientId = try {
                val listenTimeout = SUBSCRIPTION_INTERVAL.plus(5.seconds)
                client.connect(listenTimeout)
            } catch (e: CometdClient.CometdException) {
                handleFailure(e)
                return@launch
            }

            connectionScope = this
            // Subscribe to server status; disconnect on subscription failure
            try {
                client.subscribe(CometdClient.Channels.serverStatus(clientId))
                    .onEach { message -> parseServerStatus(message) }
                    .onCompletion { if (isActive) disconnect() }
                    .launchIn(this)
            } catch (e: CometdClient.CometdException) {
                handleFailure(e)
                return@launch
            }

            try {
                publishRequest(
                    ServerStatusRequest(SUBSCRIPTION_INTERVAL),
                    responseChannel = CometdClient.Channels.serverStatus(clientId)
                )
            } catch (e: CometdClient.CometdException) {
                handleFailure(e)
                return@launch
            }

            // Wait for connection being established, which happens once the first
            // server status message arrives
            withTimeoutOrNull(CONNECTION_TIMEOUT) {
                stateFlow.filter { it is ConnectionState.Connected }.first()
            } ?: handleFailure(CometdClient.CometdException("Initial server status timeout"))

            // Schedule disconnection once all subscribers vanish
            stateFlow.subscriptionCount
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .onEach { hasSubscribers ->
                    pendingDisconnectJob?.cancel()
                    if (!hasSubscribers) {
                        pendingDisconnectJob = launch {
                            delay(IDLE_DISCONNECT_TIMEOUT)
                            disconnect()
                        }
                    }
                }
                .launchIn(this)
        }
    }

    fun playerState(playerId: PlayerId): Flow<PlayerState> = stateFlow
        .mapNotNull { (it as? ConnectionState.Connected)?.players?.find { p -> p.id == playerId } }
        .mapNotNull { p -> playerStates[p.id] }

    suspend fun fetchItemsForAction(
        playerId: PlayerId,
        action: JiveAction,
        page: PagingParams
    ): SlimBrowseItemList {
        val response = doRequestWithResult<SlimBrowseListResponse>(
            FetchItemsForActionRequest(playerId, action, page)
        )
        // If we're browsing an album list, augment it with release years
        return when {
            page.start == "0" && page.page == "1" -> response.asModelItems(json)
            action.fetchesAlbumList() -> {
                val yearsById = doRequestWithResult<AlbumInfoListResponse>(
                    FetchAlbumInfoRequest(playerId, action)
                ).yearsById()
                response.asModelItems(json) { itemObject ->
                    itemObject.extractSlimBrowseAlbumIdForAlbumListResponse()
                        ?.let { yearsById[it] }
                        ?.takeIf { it != 0 }
                        ?.toString()
                }
            }
            action.fetchesAlbumTrackList() -> {
                val durationsById = doRequestWithResult<TrackInfoListResponse>(
                    FetchTrackInfoRequest(playerId, action)
                ).durationsById()
                response.asModelItems(json) { itemObject ->
                    itemObject.extractSlimBrowseTrackIdForTrackListResponse()
                        ?.let { durationsById[it] }
                        ?.let { duration ->
                            duration.toComponents { minutes, seconds, _ ->
                                "$minutes:${seconds.toString().padStart(2, '0')}"
                            }
                        }
                }
            }
            else -> response.asModelItems(json)
        }
    }

    suspend fun fetchSongInfosForDownload(data: DownloadRequestData) =
        doRequestWithResult<DownloadSongInfoListResponse>(
            FetchSongInfosForDownloadRequest(data)
        ).asModelItems(mediaDirectories)

    suspend fun fetchSlideshowImages(playerId: PlayerId, action: JiveAction) =
        doRequestWithResult<SlideshowListResponse>(
            FetchItemsForActionRequest(playerId, action, PagingParams.All)
        ).items

    suspend fun fetchPlaylist(playerId: PlayerId, page: PagingParams) =
        doRequestWithResult<PlayerStatusResponse>(
            PlayerStatusRequest(playerId, page)
        ).asModelPlaylist(json, page.start.toInt())

    suspend fun movePlaylistItem(playerId: PlayerId, fromPosition: Int, toPosition: Int) =
        publishOneShotRequest(MovePlaylistItemRequest(playerId, fromPosition, toPosition))

    suspend fun removePlaylistItem(playerId: PlayerId, position: Int) =
        publishOneShotRequest(RemovePlaylistItemRequest(playerId, position))

    suspend fun advanceToPlaylistPosition(playerId: PlayerId, position: Int) =
        publishOneShotRequest(
            SetPlaylistPositionRequest(playerId, position, appContext.prefs.fadeInDuration)
        )

    suspend fun clearCurrentPlaylist(playerId: PlayerId) =
        publishOneShotRequest(ClearPlaylistRequest(playerId))

    suspend fun saveCurrentPlaylist(playerId: PlayerId, name: String) =
        publishOneShotRequest(SavePlaylistRequest(playerId, name))

    suspend fun executeAction(playerId: PlayerId, action: JiveAction) =
        publishOneShotRequest(ExecuteActionRequest(playerId, action))

    suspend fun changePlaybackState(playerId: PlayerId, state: PlayerStatus.PlayState) {
        val currentState = playerStates[playerId]
            ?.playStatus
            ?.replayCache
            ?.lastOrNull()
            ?.playbackState ?: PlayerStatus.PlayState.Stopped
        val request = when (state) {
            PlayerStatus.PlayState.Stopped -> ChangePlaybackStateRequest.Stop(playerId)
            PlayerStatus.PlayState.Paused -> if (currentState == PlayerStatus.PlayState.Playing) {
                ChangePlaybackStateRequest.Pause(playerId)
            } else {
                null
            }
            PlayerStatus.PlayState.Playing -> if (currentState == PlayerStatus.PlayState.Paused) {
                ChangePlaybackStateRequest.Unpause(playerId, appContext.prefs.fadeInDuration)
            } else {
                ChangePlaybackStateRequest.Play(playerId, appContext.prefs.fadeInDuration)
            }
        } ?: return
        publishOneShotRequest(request)
    }

    suspend fun syncPlayers(masterPlayerId: PlayerId, slavePlayerId: PlayerId) =
        publishOneShotRequest(SyncPlayersRequest(masterPlayerId, slavePlayerId))
    suspend fun unsyncPlayer(playerId: PlayerId) =
        publishOneShotRequest(UnsyncPlayerRequest(playerId))

    suspend fun setMuteState(playerId: PlayerId, muted: Boolean) =
        publishOneShotRequest(SetMuteStateRequest(playerId, muted))

    suspend fun setVolume(playerId: PlayerId, volume: Int) =
        publishOneShotRequest(SetVolumeRequest(playerId, volume))

    suspend fun togglePower(playerId: PlayerId) =
        publishOneShotRequest(PlayerPowerRequest(playerId, null))
    suspend fun setPowerState(playerId: PlayerId, on: Boolean) =
        publishOneShotRequest(PlayerPowerRequest(playerId, on))
    suspend fun sendButtonRequest(request: PlaybackButtonRequest) = publishOneShotRequest(request)

    suspend fun updatePlaybackPosition(playerId: PlayerId, positionSeconds: Int) =
        publishOneShotRequest(SetPlaybackPositionRequest(playerId, positionSeconds))

    suspend fun getLocalLibrarySearchResultCounts(
        searchTerm: String
    ): LocalLibrarySearchResultCounts {
        val results = doRequestWithResult<LocalSearchResultsResponse>(
            LocalSearchRequest(searchTerm, PagingParams.CountOnly)
        )
        return LocalLibrarySearchResultCounts(
            results.albumCount,
            results.artistCount,
            results.genreCount,
            results.trackCount
        )
    }

    suspend fun getLocalLibrarySearchResults(
        playerId: PlayerId,
        searchTerm: String,
        type: LibrarySearchRequest.Mode,
        page: PagingParams
    ) = doRequestWithResult<SlimBrowseListResponse>(
        LibrarySearchRequest(playerId, searchTerm, type, page)
    ).asModelItems(json)

    suspend fun getRadioSearchResults(playerId: PlayerId, searchTerm: String, page: PagingParams) =
        doRequestWithResult<SlimBrowseListResponse>(
            RadioSearchRequest(playerId, searchTerm, page)
        ).asModelItems(json)

    private suspend fun fetchHomeMenu(playerId: PlayerId) =
        doRequestWithResult<JiveHomeItemListResponse>(
            FetchHomeMenuRequest(playerId)
        ).asModelItems(appContext).associateBy { it.id }

    private suspend inline fun <reified T> doRequestWithResult(request: Request): T {
        val jsonData = publishOneShotRequest(request)
        return json.decodeFromJsonElement<T>(jsonData)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun publishOneShotRequest(request: Request): JsonElement {
        val client = client
        val clientId = client?.clientId ?: throw IllegalStateException()
        val scope = connectionScope ?: throw IllegalStateException()
        val id = nextRequestId++
        val responseChannel = CometdClient.Channels.oneShotRequestResponse(clientId, id.toString())
        val subscriptionChannel = scope.produce {
            try {
                val subscriptionFlow = client.subscribe(responseChannel)
                val r = subscriptionFlow.map { it.data }.filterNotNull().first()
                send(r)
            } catch (e: CometdClient.CometdException) {
                cancel("Subscribing to publish request failed", e)
            }
        }
        yield() // make sure subscription coroutine is started
        return try {
            publishRequest(request, responseChannel = responseChannel)
            withTimeoutOrNull(CONNECTION_TIMEOUT) {
                subscriptionChannel.receive()
            } ?: throw CometdClient.CometdException("Response timeout")
        } catch (e: CometdClient.CometdException) {
            handleFailure(e)
            coroutineContext.cancel(CancellationException("Fetching publish response failed", e))
            awaitCancellation()
        }
    }

    @Throws(CometdClient.CometdException::class)
    private suspend fun publishRequest(
        request: Request,
        channel: String = CometdClient.Channels.oneShotRequest(),
        responseChannel: String
    ) {
        val data = buildJsonObject {
            put("request", request.toMessageJson())
            put("response", responseChannel)
        }
        client?.publish(channel, data)
    }

    private suspend fun parseServerStatus(message: CometdClient.Message) {
        val clientId = client?.clientId ?: return
        val response = message.data?.let { json.decodeFromJsonElement<ServerStatusResponse>(it) }
            ?: return
        var subscriptionFailure: CometdClient.CometdException? = null
        // Create player states for newly reported players
        response.players.filter { !playerStates.containsKey(it.id) }.forEach { p ->
            playerStates[p.id] = PlayerState(coroutineContext, this, clientId, p.id)
            try {
                publishRequest(
                    PlayerStatusRequest(p.id),
                    responseChannel = CometdClient.Channels.playerStatus(clientId, p.id)
                )
            } catch (e: CometdClient.CometdException) {
                subscriptionFailure = e
            }
        }
        // Destroy player states for players no longer in the list
        playerStates.keys
            .filter { playerId -> !response.players.any { it.id == playerId } }
            .forEach { playerStates.remove(it)?.cancel() }

        mediaDirectories = response.mediaDirectories
        subscriptionFailure.let { failure ->
            if (failure == null) {
                stateFlow.emit(ConnectionState.Connected(response.players))
            } else {
                handleFailure(failure)
            }
        }
    }

    private fun disconnect(newState: ConnectionState = ConnectionState.Disconnected) {
        client?.disconnect()
        client = null
        connectionScope?.cancel()
        connectionScope = null
        stateFlow.tryEmit(newState)
        playerStates.values.forEach { it.cancel() }
        playerStates.clear()
    }

    private fun handleFailure(cause: CometdClient.CometdException) =
        disconnect(ConnectionState.ConnectionFailure(cause))

    class PlayerState(
        parentContext: CoroutineContext,
        private val connectionHelper: ConnectionHelper,
        clientId: String,
        val playerId: PlayerId
    ) : CoroutineScope {
        override val coroutineContext = parentContext + SupervisorJob()

        private val homeMenuFlow = createSubscribingFlow(
            { subscribed ->
                if (subscribed) {
                    val request = MenuStatusRequest(playerId)
                    connectionHelper.publishRequest(
                        request,
                        CometdClient.Channels.subscribe(),
                        CometdClient.Channels.menuStatus(clientId, playerId)
                    )
                } else {
                    val data = buildJsonObject {
                        put("unsubscribe", CometdClient.Channels.menuStatus(clientId, playerId))
                    }
                    connectionHelper.client?.publish(CometdClient.Channels.unsubscribe(), data)
                }
            },
            { connectionHelper.fetchHomeMenu(playerId) },
            CometdClient.Channels.menuStatus(clientId, playerId),
            { last, msgData ->
                // array items: 0 = 'menustatus', 1 = items, 2 = action (add/remove), 3 = client ID
                val nodesInMessage = msgData.jsonArray[1].jsonArray.map {
                    connectionHelper.json.parseToJiveHomeMenuItem(it.jsonObject)
                }
                val isRemoval = msgData.jsonArray[2].jsonPrimitive.content == "remove"
                last?.toMutableMap()?.apply {
                    nodesInMessage.forEach {
                        if (isRemoval) remove(it.id) else put(it.id, it)
                    }
                }
            }
        )

        private val playerStatusFlow = createSubscribingFlow(
            { subscribed ->
                val playerStatusRequest =
                    UpdatePlayerStatusSubscriptionRequest(playerId, subscribed)
                connectionHelper.publishRequest(
                    playerStatusRequest,
                    CometdClient.Channels.subscribe(),
                    CometdClient.Channels.playerStatus(clientId, playerId)
                )
            },
            {
                connectionHelper.doRequestWithResult<PlayerStatusResponse>(
                    PlayerStatusRequest(playerId)
                ).asModelStatus(connectionHelper.json)
            },
            CometdClient.Channels.playerStatus(clientId, playerId),
            { _, msgData ->
                val json = connectionHelper.json
                json.decodeFromJsonElement<PlayerStatusResponse>(msgData).asModelStatus(json)
            }
        )

        private val displayStatusFlow = createSubscribingFlow<DisplayMessage>(
            { subscribed ->
                val displayStatusRequest =
                    UpdateDisplayStatusSubscriptionRequest(playerId, subscribed)
                connectionHelper.publishRequest(
                    displayStatusRequest,
                    CometdClient.Channels.subscribe(),
                    CometdClient.Channels.displayStatus(clientId, playerId)
                )
            },
            { null },
            CometdClient.Channels.displayStatus(clientId, playerId),
            { _, msgData ->
                if (msgData.jsonObject.isEmpty()) {
                    null
                } else {
                    val json = connectionHelper.json
                    json.decodeFromJsonElement<DisplayStatusResponse>(msgData).display
                }
            }
        )

        val homeMenu: SharedFlow<Map<String, JiveHomeMenuItem>> = homeMenuFlow.asSharedFlow()
        val playStatus: SharedFlow<PlayerStatus> = playerStatusFlow.asSharedFlow()
        val displayStatus: SharedFlow<DisplayMessage> = displayStatusFlow.asSharedFlow()

        private fun <T> createSubscribingFlow(
            subscribeMethod: suspend (Boolean) -> Unit,
            requestMethod: suspend () -> T?,
            responseChannel: String,
            updateFromEventMethod: (T?, JsonElement) -> T?
        ) = MutableSharedFlow<T>(1).apply {
            val jobHolder = SubscriptionFlowJobHolder(this@PlayerState)
            subscriptionCount
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .onEach { subscribed ->
                    try {
                        subscribeMethod(subscribed)
                    } catch (e: CometdClient.CometdException) {
                        jobHolder.cancel(e)
                    }
                    if (subscribed) {
                        requestMethod()?.let { emit(it) }
                        jobHolder.launch {
                            try {
                                val flow = connectionHelper.client?.subscribe(responseChannel)
                                flow?.collect { msg ->
                                    val last = replayCache.lastOrNull()
                                    val data = msg.data ?: return@collect
                                    updateFromEventMethod(last, data)?.let { emit(it) }
                                }
                            } catch (e: CometdClient.CometdException) {
                                cancel("Subscription failed", e)
                            }
                        }
                    } else {
                        jobHolder.cancel()
                    }
                }
                .launchIn(this@PlayerState)
        }

        private class SubscriptionFlowJobHolder(private val scope: CoroutineScope) {
            private var job: Job? = null
            fun launch(block: suspend CoroutineScope.() -> Unit) {
                job?.cancel()
                job = scope.launch(block = block)
            }
            fun cancel(cause: Throwable? = null) {
                job?.cancel(cause?.let { CancellationException(it.message, it) })
                job = null
            }
        }
    }

    companion object {
        private val SUBSCRIPTION_INTERVAL = 60.seconds
        private val CONNECTION_TIMEOUT = 5.seconds
        private val IDLE_DISCONNECT_TIMEOUT = 10.seconds
    }
}
