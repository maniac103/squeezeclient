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

import android.util.Log
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.extfuncs.ValueOrCompletion
import de.maniac103.squeezeclient.extfuncs.dematerializeCompletion
import de.maniac103.squeezeclient.extfuncs.materializeCompletion
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor

class CometdClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val serverConfig: ServerConfiguration
) {
    private val requestClient = httpClient.newBuilder()
        .apply {
            if (HTTP_LOGGING_LEVEL != HttpLoggingInterceptor.Level.NONE) {
                val logger = HttpLoggingInterceptor.Logger { message -> Log.d(TAG, message) }
                HttpLoggingInterceptor(logger).apply {
                    level = HTTP_LOGGING_LEVEL
                    addInterceptor(this)
                }
            }
        }
        .build()
    var clientId: String? = null
        private set
    private var listenScope: CoroutineScope? = null
    private var eventFlow: Flow<ValueOrCompletion<Message>>? = null
    private var nextId = 1

    @Throws(CometdException::class)
    suspend fun connect(listenTimeout: Duration): String {
        val existingClientId = clientId
        if (existingClientId != null && eventFlow != null) {
            return existingClientId
        }
        val newClientId = handshake()
        eventFlow = startListening(newClientId, listenTimeout)
        Log.d(TAG, "Connected to ${serverConfig.url} with client ID $newClientId")
        clientId = newClientId
        return newClientId
    }

    fun disconnect() {
        clientId = null
        eventFlow = null
        listenScope?.cancel()
        listenScope = null
        Log.d(TAG, "Disconnected from ${serverConfig.url}")
    }

    @Throws(CometdException::class)
    fun subscribe(responseChannel: String): Flow<Message> {
        val events = eventFlow ?: throw CometdException("Not connected")
        val responseChannelId = ChannelId(responseChannel)
        return events
            .dematerializeCompletion()
            .filter { responseChannelId.matches(it.channelId) }
    }

    @Throws(CometdException::class)
    suspend fun publish(channel: String, messageData: JsonElement) {
        val clientId = this.clientId ?: throw CometdException("Not connected")
        val message = buildJsonObject {
            put("channel", channel)
            put("clientId", clientId)
            put("data", messageData)
            put("id", nextId++)
        }
        val request = buildRequest(message)
        executeRequestAndGetResponseBody(requestClient, request).use { body ->
            val content = withContext(Dispatchers.IO) {
                body.string()
            }
            val messages = content.parseToMessageArrayOrThrow()
            if (!messages[0].successful) {
                throw CometdException("Unexpected response for request $messageData: $content")
            }
        }
    }

    @Throws(CometdException::class)
    private suspend fun handshake(): String {
        val message = buildJsonObject {
            put("channel", "/meta/handshake")
            put("id", nextId++)
            put("supportedConnectionTypes", "streaming")
            put("version", "1.0")
        }

        val request = buildRequest(message)
        val messages = executeRequestAndGetResponseBody(requestClient, request).use { body ->
            withContext(Dispatchers.IO) {
                body.string().parseToMessageArrayOrThrow()
            }
        }
        return messages[0].clientId ?: throw CometdException("No client ID in $messages")
    }

    @Throws(CometdException::class)
    private suspend fun startListening(
        clientId: String,
        listenTimeout: Duration
    ): Flow<ValueOrCompletion<Message>> {
        val connectMessage = buildJsonObject {
            put("channel", "/meta/connect")
            put("id", nextId++)
            put("connectionType", "streaming")
            put("clientId", clientId)
        }
        val metaSubscribeMessage = buildJsonObject {
            put("channel", "/meta/subscribe")
            put("subscription", "/$clientId/**")
            put("clientId", clientId)
            put("id", nextId++)
        }
        val request = buildRequest(connectMessage, metaSubscribeMessage)
        val subscriptionClient = httpClient.newBuilder()
            .readTimeout(listenTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
        val body = executeRequestAndGetResponseBody(subscriptionClient, request)
        val listenScope = CoroutineScope(Dispatchers.Main) + SupervisorJob()
        val channel = readFromEventStream(body, listenScope)

        try {
            withTimeout(5.seconds) {
                val connectResponse = channel.receive()
                if (!connectResponse.successful) {
                    listenScope.cancel()
                    throw CometdException("Connection unsuccessful")
                }
                val metaSubscribeResponse = channel.receive()
                if (!metaSubscribeResponse.successful) {
                    listenScope.cancel()
                    throw CometdException("Initial subscription unsuccessful")
                }
            }
        } catch (e: TimeoutCancellationException) {
            listenScope.cancel()
            throw CometdException("Subscription response missing", e)
        }

        // From now on the channel only yields event messages
        this.listenScope = listenScope
        return channel.receiveAsFlow()
            .materializeCompletion()
            .shareIn(listenScope, SharingStarted.WhileSubscribed())
    }

    @Throws(CometdException::class)
    private suspend fun executeRequestAndGetResponseBody(
        client: OkHttpClient,
        request: Request
    ): ResponseBody = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw CometdException("Could not execute HTTP request", e)
        }
        if (!response.isSuccessful) throw CometdException("HTTP error ${response.code}")
        response.body ?: throw CometdException("Empty response body")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readFromEventStream(body: ResponseBody, scope: CoroutineScope) = scope.produce {
        Log.d(TAG, "Connected to event stream")
        try {
            val buf = ByteArray(4096)
            val source = body.source().inputStream()
            withContext(Dispatchers.IO) {
                val builder = StringBuilder()
                while (isActive) {
                    val readByteCount = try {
                        source.read(buf)
                    } catch (e: IOException) {
                        cancel("Listen failure", e)
                        return@withContext
                    }
                    if (readByteCount < 0) {
                        cancel("EOF while reading event stream")
                        return@withContext
                    }
                    builder.append(String(buf, 0, readByteCount, Charsets.UTF_8))
                    when (builder.takeLast(2)) {
                        "[]" -> {
                            // empty message array
                            builder.clear()
                        }

                        "}]" -> {
                            // content looks like valid JSON, try to parse it
                            try {
                                builder.toString().parseToMessageArrayOrThrow().forEach {
                                    it.logIfNeeded(builder.length)
                                    send(it)
                                }
                                builder.clear()
                            } catch (e: CometdException) {
                                // JSON was incomplete
                            }
                        }
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) {
                body.close()
            }
            Log.d(TAG, "Disconnected from event stream")
        }
    }

    private fun buildRequest(vararg messages: JsonObject): Request {
        val messagesArray = buildJsonArray {
            messages.forEach { add(it) }
        }
        val mimeType = "application/json".toMediaType()
        return Request.Builder().apply {
            url("${serverConfig.url}cometd")
            // FIXME: pretend to be Squeezer as there's UA filtering in some plugins (e.g. Qobuz)
            //        -> at some point we should get added to the UA list instead
            header("User-Agent", "Squeezer-squeezer/1.0")
            method("POST", json.encodeToString(messagesArray).toRequestBody(mimeType))
        }.build()
    }

    private fun String.parseToMessageArrayOrThrow() = try {
        json.decodeFromString<Array<Message>>(this)
    } catch (e: SerializationException) {
        throw CometdException("Could not parse JSON response $this", e)
    }

    @Serializable
    data class Message(
        @SerialName("channel") val channelId: ChannelId,
        val clientId: String? = null,
        val id: Int,
        val successful: Boolean = true,
        val data: JsonElement? = null
    ) {
        fun logIfNeeded(jsonLength: Int) {
            when (HTTP_LOGGING_LEVEL) {
                HttpLoggingInterceptor.Level.BODY ->
                    Log.d(TAG, "Received event message ($jsonLength bytes): $this")

                HttpLoggingInterceptor.Level.BASIC, HttpLoggingInterceptor.Level.HEADERS ->
                    Log.d(TAG, "Received event message ($jsonLength bytes) for $channelId")

                else -> {}
            }
        }
    }

    @Serializable(with = ChannelIdSerializer::class)
    data class ChannelId(val channel: String) {
        private val segments = channel.split("/")

        fun matches(other: ChannelId): Boolean {
            if (segments.size > other.segments.size) {
                return false
            }
            for (i in segments.indices) {
                val elem = segments[i]
                val otherElem = other.segments.elementAtOrNull(i)
                when {
                    elem == "**" -> return true
                    otherElem == null -> return false
                    elem != "*" && elem != otherElem -> return false
                }
            }
            return segments.size == other.segments.size
        }
    }

    class CometdException(message: String, cause: Throwable? = null) : Exception(message, cause)

    object Channels {
        fun oneShotRequest() = "/slim/request"
        fun oneShotRequestResponse(clientId: String, messageId: String) =
            "/$clientId/slim/request/$messageId"

        fun subscribe() = "/slim/subscribe"
        fun unsubscribe() = "/slim/unsubscribe"

        fun serverStatus(clientId: String) = "/$clientId/slim/serverstatus"
        fun playerStatus(clientId: String, playerId: PlayerId?) =
            "/$clientId/slim/playerstatus/${playerId?.channelId ?: "*"}"
        fun displayStatus(clientId: String, playerId: PlayerId?) =
            "/$clientId/slim/displaystatus/${playerId?.channelId ?: "*"}"
        fun menuStatus(clientId: String, playerId: PlayerId?) =
            "/$clientId/slim/menustatus/${playerId?.channelId ?: "*"}"
    }

    companion object {
        private const val TAG = "CometdClient"
        private val HTTP_LOGGING_LEVEL = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
}
