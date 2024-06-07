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

import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object BooleanAsIntSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor get() =
        PrimitiveSerialDescriptor("BooleanAsInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Boolean {
        return decoder.decodeInt() != 0
    }
    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeInt(if (value) 1 else 0)
    }
}

object TimestampAsInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor get() =
        PrimitiveSerialDescriptor("TimestampAsInstant", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Instant {
        val epochSecondsDouble = decoder.decodeDouble()
        val epochSecondsLong = epochSecondsDouble.toLong()
        val remainderNanoSeconds = ((epochSecondsDouble - epochSecondsLong) * 1000000000.0).toLong()
        return Instant.fromEpochSeconds(epochSecondsLong, remainderNanoSeconds)
    }
    override fun serialize(encoder: Encoder, value: Instant) {
        val seconds = value.epochSeconds.toDouble()
        val remainder = value.nanosecondsOfSecond.toDouble() / 1000000000.0
        encoder.encodeDouble(seconds + remainder)
    }
}

object PlayerIdSerializer : KSerializer<PlayerId> {
    override val descriptor: SerialDescriptor get() =
        PrimitiveSerialDescriptor("PlayerId", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): PlayerId {
        return PlayerId(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: PlayerId) {
        encoder.encodeString(value.id)
    }
}

object PlayerIdListAsStringSerializer : KSerializer<List<PlayerId>> {
    override val descriptor: SerialDescriptor get() =
        PrimitiveSerialDescriptor("PlayerIdList", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): List<PlayerId> {
        return decoder.decodeString().split(',').map { PlayerId(it) }
    }
    override fun serialize(encoder: Encoder, value: List<PlayerId>) {
        encoder.encodeString(value.joinToString(",") { it.id })
    }
}

object ChannelIdSerializer : KSerializer<CometdClient.ChannelId> {
    override val descriptor: SerialDescriptor get() =
        PrimitiveSerialDescriptor("PlayerId", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): CometdClient.ChannelId {
        return CometdClient.ChannelId(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: CometdClient.ChannelId) {
        encoder.encodeString(value.channel)
    }
}
