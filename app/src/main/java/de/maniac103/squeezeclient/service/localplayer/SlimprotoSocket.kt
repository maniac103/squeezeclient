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

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import de.maniac103.squeezeclient.extfuncs.getOrCreateDeviceIdentifier
import de.maniac103.squeezeclient.extfuncs.putAsAscii
import de.maniac103.squeezeclient.extfuncs.readRemainderAsArray
import de.maniac103.squeezeclient.extfuncs.readString
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.extfuncs.skip
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import okio.buffer
import okio.sink
import okio.source

class SlimprotoSocket(prefs: SharedPreferences) {
    val host: String? = prefs.serverConfig?.url?.host
    private val deviceIdentifier: UUID = prefs.getOrCreateDeviceIdentifier()
    private var socket: SocketHolder? = null

    private val packetParsers: Map<String, (ByteBuffer) -> CommandPacket> = mapOf(
        "aude" to this::parseAudioEnablePacket,
        "audg" to this::parseAudioGainPacket,
        "cont" to { CommandPacket.Continue },
        "setd" to this::parseSettingPacket,
        "strm" to this::parseStreamPacket
    )

    suspend fun connect() = connectInternal(false)
    suspend fun reconnect() = connectInternal(true)

    private suspend fun connectInternal(reconnect: Boolean): Boolean {
        teardownSocket()
        createSocket()
        return if (socket != null) {
            sendHello(reconnect)
            true
        } else {
            false
        }
    }

    suspend fun disconnect() {
        sendBye()
        teardownSocket()
    }

    private suspend fun createSocket() {
        socket = try {
            host?.let { SocketHolder(it, 3483) }
        } catch (e: IOException) {
            Log.d(TAG, "Could not connect to $host", e)
            null
        }
    }

    private fun teardownSocket() {
        socket?.close()
        socket = null
    }

    enum class SettingType(val id: Byte) {
        PlayerName(0.toByte())
    }

    sealed class CommandPacket {
        data class AudioEnable(val spdifEnable: Boolean, val dacEnable: Boolean) : CommandPacket()
        data class AudioGain(
            val digitalVolume: Boolean,
            val preamp: UByte,
            val newLeft: Float,
            val newRight: Float
        ) : CommandPacket()
        data object Continue : CommandPacket()

        data class StreamStart(
            val uri: Uri,
            val method: String,
            val headers: Map<String, String>,
            val autoStart: Boolean,
            val directStreaming: Boolean,
            val mimeType: String?,
            val replayGain: Float
        ) : CommandPacket()
        data class StreamPause(val pauseInterval: Duration?) : CommandPacket()
        data class StreamUnpause(val unpauseTimestamp: Int) : CommandPacket()
        data object StreamStop : CommandPacket()
        data class StreamStatus(val timestamp: Int) : CommandPacket()
        data object StreamFlush : CommandPacket()
        data class StreamSkipAhead(val skipOverInterval: Duration) : CommandPacket()

        data class TriggerGetSetting(val type: SettingType) : CommandPacket()
        class SetSetting(val type: SettingType, val data: ByteArray) : CommandPacket() {
            @OptIn(ExperimentalStdlibApi::class)
            override fun toString() = "${javaClass.name}(type=$type, data=${data.toHexString()})"
        }

        data class Other(val type: String, val payloadLen: Int) : CommandPacket()
    }

    suspend fun readNextCommand(): Result<CommandPacket> {
        val readResult = socket?.readPacket() ?: throw IllegalStateException()
        val data = readResult.fold(
            onSuccess = { it },
            onFailure = { return Result.failure(it) }
        )
        val buffer = ByteBuffer.wrap(data)
        val command = buffer.readString(4)

        val result = runCatching {
            val parser = packetParsers[command]
            parser?.invoke(buffer) ?: CommandPacket.Other(command, buffer.remaining())
        }
        Log.d(TAG, "Read command packet: $result")
        return result
    }

    private fun parseAudioEnablePacket(buffer: ByteBuffer): CommandPacket.AudioEnable {
        if (buffer.remaining() != 2) throw IllegalArgumentException()
        val spdifEnable = buffer.get() != 0.toByte()
        val dacEnable = buffer.get() != 0.toByte()
        return CommandPacket.AudioEnable(spdifEnable, dacEnable)
    }

    private fun parseAudioGainPacket(buffer: ByteBuffer): CommandPacket.AudioGain {
        if (buffer.remaining() != 18 && buffer.remaining() != 22) throw IllegalArgumentException()
        val oldLeft = buffer.getInt().toFloat() / 128F
        val oldRight = buffer.getInt().toFloat() / 128F
        val dvc = buffer.get() != 0.toByte()
        val preamp = buffer.get().toUByte()
        // ignoring the following 2 values (new-style left and right gains as fixed point 16.16
        // values) since those are on a logarithmic scale while we want a linear scale
        buffer.skip(8)
        // last value: optional sequence number
        return CommandPacket.AudioGain(dvc, preamp, oldLeft, oldRight)
    }

    private fun parseSettingPacket(buffer: ByteBuffer): CommandPacket {
        if (!buffer.hasRemaining()) throw IllegalArgumentException()
        val id = buffer.get()
        val setting = SettingType.entries.firstOrNull { it.id == id }
        return when {
            setting == null -> {
                Log.d(TAG, "Got setd for unknown setting $id")
                CommandPacket.Other("setd", buffer.remaining())
            }
            buffer.hasRemaining() -> {
                val settingData = buffer.readRemainderAsArray()
                CommandPacket.SetSetting(setting, settingData)
            }
            else -> CommandPacket.TriggerGetSetting(setting)
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun parseStreamPacket(buffer: ByteBuffer): CommandPacket {
        if (buffer.remaining() < 24) throw IllegalArgumentException()
        val commandByte = buffer.get().toInt().toChar()
        val (autostart, directStreaming) = when (val t = buffer.get().toInt().toChar()) {
            '0' -> Pair(false, false)
            '1' -> Pair(true, false)
            '2' -> Pair(false, true)
            '3' -> Pair(true, true)
            else -> throw IllegalArgumentException("Unexpected STRM auto-start value $t")
        }
        val mimeType = when (val t = buffer.get().toInt().toChar()) {
            'm' -> "audio/mpeg" // MP3
            'f' -> "audio/flac" // FLAC
            'o' -> "audio/ogg" // OGG
            'a' -> "audio/mp4a-latm" // AAC
            '?' -> null
            else -> throw IllegalArgumentException("Unexpected STRM format value $t")
        }

        buffer.skip(4) // PCM sample size, sample rate, channels, endianness
        buffer.skip(4) // thresholdKb, spdifEnable, transitionPeriodSeconds, transitionType
        val flags = buffer.get()
        buffer.skip(2) // outputThresholdSeconds, reserved
        val replayGainOrValue = buffer.getInt()
        val port = buffer.getShort().toInt().let { if (it != 0) it else 9000 }
        val host = buffer.getInt().let {
            if (it == 0) {
                host ?: throw IllegalArgumentException()
            } else {
                val bytes = UByteArray(4) { i -> (it shr (i * 8)).toUByte() }
                "${bytes[3]}.${bytes[2]}.${bytes[1]}.${bytes[0]}"
            }
        }
        return when (commandByte) {
            's' -> {
                val lines = buffer.readString(buffer.remaining())
                    .split("\r\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (lines.isEmpty()) throw IllegalArgumentException()
                val requestParts = lines[0].split(' ')
                if (requestParts.size != 3) {
                    throw IllegalArgumentException("Unexpected HTTP request ${lines[0]}")
                }
                val headers = lines.subList(1, lines.size)
                    .associate {
                        val splitted = it.split(':', limit = 2)
                        splitted[0] to splitted.getOrElse(1) { "" }
                    }
                val useSsl = (flags.toInt() and 0x20) != 0
                val uriString = if (requestParts[1].startsWith("/")) {
                    val scheme = if (useSsl) "https" else "http"
                    val actualHost = headers.getOrDefault("Host", host).trim()
                    "$scheme://$actualHost:$port${requestParts[1]}"
                } else {
                    requestParts[1]
                }
                CommandPacket.StreamStart(
                    uriString.toUri(),
                    requestParts[0],
                    headers,
                    autostart,
                    directStreaming,
                    mimeType,
                    replayGainOrValue.toFloat() / 65536F
                )
            }
            'p' -> CommandPacket.StreamPause(
                replayGainOrValue
                    .takeIf { it > 0 }
                    ?.toDuration(DurationUnit.MILLISECONDS)
            )
            'u' -> CommandPacket.StreamUnpause(replayGainOrValue)
            'q' -> CommandPacket.StreamStop
            't' -> CommandPacket.StreamStatus(replayGainOrValue)
            'f' -> CommandPacket.StreamFlush
            'a' -> CommandPacket.StreamSkipAhead(
                replayGainOrValue.toDuration(DurationUnit.MILLISECONDS)
            )
            else -> throw IllegalArgumentException("Unexpected STRM command $commandByte")
        }
    }

    private suspend fun sendHello(reconnect: Boolean) {
        val supportedFormats = listOf("mp3", "aac", "ogg", "flc")
        val supportedCapabilities = listOf(
            "Model=squeezeclient",
            "AccuratePlayPoints=1",
            "CanHTTPS=1"
        )
        val macBytes = ByteArray(6) { i ->
            (deviceIdentifier.leastSignificantBits shr (i * 8)).toByte()
        }
        val capabilityString = (supportedFormats + supportedCapabilities).joinToString(",")
        val payload = ByteBuffer.allocate(36 + capabilityString.length).apply {
            put(12) // device ID
            put(1) // revision
            put(macBytes) // MAC
            putLong(deviceIdentifier.mostSignificantBits) // UUID
            putLong(deviceIdentifier.leastSignificantBits)
            putShort(if (reconnect) 0x4000 else 0) // WLAN channel list
            putLong(0) // bytes received
            putShort(0) // language
            putAsAscii(capabilityString)
        }
        sendCommandPacket("HELO", payload)
    }

    private suspend fun sendBye() {
        sendCommandPacket("BYE!", null)
    }

    suspend fun sendDisconnect() {
        val payload = ByteBuffer.allocate(1).apply {
            // possible codes: DISCONNECT_OK = 0, LOCAL_DISCONNECT = 1, REMOTE_DISCONNECT = 2, UNREACHABLE = 3, TIMEOUT = 4
            put(0)
        }
        sendCommandPacket("DSCO", payload)
    }

    suspend fun sendResponseReceived(resp: Response) {
        val headers = resp.headers.joinToString("\n") { (k, v) -> "$k: $v" }
        val headersString =
            "${resp.protocol.toString().uppercase()} ${resp.code} ${resp.message}\n$headers"
        val headersBytes = headersString.toByteArray(Charsets.US_ASCII)
        sendCommandPacket("RESP", ByteBuffer.wrap(headersBytes))
    }

    suspend fun sendMediaMetaData(title: CharSequence, artworkUri: Uri?) {
        val metadataLines = listOfNotNull(
            "StreamTitle='$title'",
            artworkUri?.let { "StreamUrl='$it'" }
        )
        val metadataBytes = metadataLines
            .joinToString("\r\n")
            .toByteArray(Charsets.US_ASCII)
        sendCommandPacket("META", ByteBuffer.wrap(metadataBytes))
    }

    suspend fun sendSetSetting(type: SettingType, data: ByteArray) {
        val payload = ByteBuffer.allocate(data.size + 1).apply {
            put(type.id)
            put(data)
        }
        sendCommandPacket("SETD", payload)
    }

    sealed class StatusType(val type: String) {
        data class Timer(val timestamp: Int) : StatusType("STMt")
        data object Start : StatusType("STMs")
        data object Connect : StatusType("STMc")
        data object Pause : StatusType("STMp")
        data object Resume : StatusType("STMr")
        data object Flushed : StatusType("STMf")
        data object Ready : StatusType("STMd")
        data object Underrun : StatusType("STMu")
    }

    suspend fun sendStatus(
        type: StatusType,
        uptime: Duration,
        playbackPosition: Duration,
        totalBytesTransferred: Long
    ) {
        val payload = ByteBuffer.allocate(53).apply {
            putAsAscii(type.type)
            put(0.toByte()) // # of CR/LF received
            put('m'.code.toByte()) // MAS initialized (m or p)
            put(0.toByte()) // MAS mode
            putInt(1) // buffer size in bytes
            putInt(0) // fullness in bytes
            putLong(totalBytesTransferred) // bytes received
            putShort(101) // wireless signal strength
            putInt(uptime.inWholeMilliseconds.toInt()) // jiffies (1 kHz timer)
            putInt(1) // output buffer size
            putInt(0) // output buffer fullness
            putInt(playbackPosition.inWholeSeconds.toInt()) // elapsed seconds
            putShort(0) // voltage
            putInt(playbackPosition.inWholeMilliseconds.toInt()) // elapsed milliseconds
            putInt(if (type is StatusType.Timer) type.timestamp else 0) // server timestamp
            putShort(0) // error code for STMn
        }
        Log.d(TAG, "Sending ${type.type} status (uptime $uptime, position $playbackPosition)")
        sendCommandPacket("STAT", payload)
    }

    private suspend fun sendCommandPacket(command: String, payload: ByteBuffer?) {
        if (command.length != 4) throw IllegalArgumentException()
        val length = payload?.capacity() ?: 0
        val data = ByteBuffer.allocate(length + 8).apply {
            putAsAscii(command)
            putInt(length)
            payload?.let {
                it.rewind()
                put(it)
            }
        }
        data.rewind()
        socket?.write(data)
    }

    class SocketHolder private constructor(private val socket: Socket) : Closeable {
        private val source = socket.source().buffer()
        private val sink = socket.sink().buffer()

        suspend fun readPacket() = withContext(Dispatchers.IO) {
            runCatching {
                synchronized(source) {
                    val len = source.readShort()
                    source.readByteArray(len.toLong())
                }
            }
        }

        suspend fun write(data: ByteBuffer) = withContext(Dispatchers.IO) {
            synchronized(sink) {
                sink.write(data)
                sink.flush()
            }
        }

        override fun close() {
            sink.close()
            source.close()
            socket.close()
        }

        companion object {
            @Throws(IOException::class)
            suspend operator fun invoke(host: String, port: Int) = withContext(Dispatchers.IO) {
                val socket = Socket()
                // Server is expected to send a command at least every 5 seconds, so set the
                // timeout slightly higher to make sure
                // - to not timeout while waiting for the next command
                // - timing out in case a command didn't arrive in time
                socket.soTimeout = 6000 // ms
                socket.connect(InetSocketAddress(host, port), 2000)
                SocketHolder(socket)
            }
        }
    }

    companion object {
        private const val TAG = "SlimprotoSocket"
    }
}
