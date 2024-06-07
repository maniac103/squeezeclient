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

import android.os.Parcelable
import de.maniac103.squeezeclient.cometd.BooleanAsIntSerializer
import de.maniac103.squeezeclient.cometd.PlayerIdSerializer
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Player(
    @SerialName("playerid")
    val id: PlayerId,
    val ip: String,
    val name: String,
    val model: String,
    @SerialName("canpoweroff")
    @Serializable(with = BooleanAsIntSerializer::class)
    val canPowerOff: Boolean,
    @Serializable(with = BooleanAsIntSerializer::class)
    val connected: Boolean
) : Parcelable

@Parcelize
@Serializable(with = PlayerIdSerializer::class)
data class PlayerId(val id: String) : Parcelable {
    @IgnoredOnParcel
    val channelId = id.filter { c -> c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' }
}
