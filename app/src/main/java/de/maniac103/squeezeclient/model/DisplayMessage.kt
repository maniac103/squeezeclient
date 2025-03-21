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

package de.maniac103.squeezeclient.model

import de.maniac103.squeezeclient.cometd.BooleanAsIntSerializer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class DisplayMessage(
    val type: MessageType? = null,
    @SerialName("duration")
    private val internalDuration: Int? = null,
    @SerialName("is-remote")
    @Serializable(with = BooleanAsIntSerializer::class)
    val isRemote: Boolean? = null,
    @SerialName("play-mode")
    val playMode: String? = null,
    @SerialName("text")
    val internalText: List<String?>,
    @SerialName("icon-id")
    // may be string or number
    private val internalIconId: JsonPrimitive? = null,
    override val icon: String? = null,
    // style = add, repeat, shuffle
    val style: String? = null
) : ArtworkItem {
    override val iconId: String? get() = internalIconId?.content
    val duration get() = internalDuration?.milliseconds
    val text get() = internalText.filterNotNull()

    @Serializable
    @Suppress("unused")
    enum class MessageType {
        @SerialName("icon")
        Icon,

        @SerialName("text")
        Text,

        @SerialName("mixed")
        Mixed,

        @SerialName("song")
        Song,

        @SerialName("popupplay")
        PopupPlay
    }
}
