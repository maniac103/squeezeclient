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

package de.maniac103.squeezeclient.cometd.response

import de.maniac103.squeezeclient.cometd.BooleanAsIntSerializer
import de.maniac103.squeezeclient.cometd.PlayerIdListAsStringSerializer
import de.maniac103.squeezeclient.cometd.PlayerIdSerializer
import de.maniac103.squeezeclient.cometd.TimestampAsInstantSerializer
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.Playlist
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toDuration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// offset and item_loop are not returned when fetching non-existing pages
@Serializable
@OptIn(ExperimentalTime::class)
data class PlayerStatusResponse(
    @SerialName("mode")
    val state: PlayerStatus.PlayState,
    val offset: String? = null,
    val count: Int,
    @SerialName("item_loop")
    private val items: List<JsonObject>? = null,
    private val base: JsonObject? = null,
    @SerialName("time")
    val playPosition: Float = 0F,
    @Serializable(with = TimestampAsInstantSerializer::class)
    val eventTimestamp: Instant = Clock.System.now(),
    @SerialName("duration")
    val currentSongDuration: Float? = null,
    @SerialName("playlist_cur_index")
    val playlistCurrentIndex: Int = 0,
    @SerialName("playlist_tracks")
    val playlistTrackCount: Int,
    @SerialName("playlist_timestamp")
    @Serializable(with = TimestampAsInstantSerializer::class)
    val playlistTimestamp: Instant = Clock.System.now(),
    @SerialName("playlist shuffle")
    val shuffleState: PlayerStatus.ShuffleState,
    @SerialName("playlist repeat")
    val repeatState: PlayerStatus.RepeatState,
    @SerialName("mixer volume")
    val currentVolume: Float? = null,
    @SerialName("digital_volume_control")
    @Serializable(with = BooleanAsIntSerializer::class)
    val digitalVolumeControl: Boolean? = null,
    @SerialName("player_name")
    val playerName: String,
    @SerialName("player_connected")
    @Serializable(with = BooleanAsIntSerializer::class)
    val connected: Boolean,
    @SerialName("power")
    @Serializable(with = BooleanAsIntSerializer::class)
    val powered: Boolean = true,
    @SerialName("sync_master")
    @Serializable(with = PlayerIdSerializer::class)
    val syncMaster: PlayerId? = null,
    @SerialName("sync_slaves")
    @Serializable(with = PlayerIdListAsStringSerializer::class)
    val syncSlaves: List<PlayerId>? = null
) {

    @Serializable
    data class Item(
        val track: String? = null,
        val artist: String? = null,
        val album: String? = null,
        @SerialName("icon-id") val iconId: String? = null,
        val icon: String? = null
    )

    fun asModelStatus(json: Json): PlayerStatus {
        val nowPlaying = items?.elementAtOrNull(0)?.asModelPlaylistItem(json, base)
        val duration = currentSongDuration
            ?.times(1000)
            ?.roundToLong()
            ?.toDuration(DurationUnit.MILLISECONDS)
        return PlayerStatus(
            state,
            PlayerStatus.PlaylistInfo(
                nowPlaying,
                playlistCurrentIndex + 1,
                playlistTrackCount,
                playlistTimestamp
            ),
            duration,
            playPosition,
            repeatState,
            shuffleState,
            playerName,
            connected,
            powered,
            currentVolume?.absoluteValue?.roundToInt(),
            currentVolume?.let { it < 0 },
            syncMaster,
            syncSlaves,
            eventTimestamp
        )
    }

    fun asModelPlaylist(json: Json, fetchOffset: Int): Playlist {
        val actualOffset = when (offset) {
            null -> fetchOffset
            "-" -> playlistCurrentIndex
            else -> offset.toInt()
        }
        val (playlist, reachableCount) = if (items != null) {
            val playlist = items.mapNotNull { it.asModelPlaylistItem(json, base) }
            val filteredItems = items.size - playlist.size
            // This assumes filtered items are at the end of the response
            val reachableCount = count - filteredItems
            playlist to reachableCount
        } else {
            emptyList<Playlist.PlaylistItem>() to count
        }
        return Playlist(
            playlist,
            actualOffset,
            reachableCount,
            playlistCurrentIndex,
            playlistTimestamp
        )
    }

    private fun JsonObject.asModelPlaylistItem(
        json: Json,
        base: JsonObject?
    ): Playlist.PlaylistItem? {
        val item = json.decodeFromJsonElement<Item>(this)
        val actions = json.combineItemAndBaseActions(this, base)
        return if (item.track != null && item.artist != null && item.album != null) {
            Playlist.PlaylistItem(
                item.track,
                item.artist,
                item.album,
                actions,
                item.iconId,
                item.icon
            )
        } else {
            null
        }
    }
}
