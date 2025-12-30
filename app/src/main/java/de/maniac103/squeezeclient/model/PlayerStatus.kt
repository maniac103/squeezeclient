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

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toDuration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalTime::class)
data class PlayerStatus(
    val playbackState: PlayState,
    val playlist: PlaylistInfo,
    val currentSongDuration: Duration? = null,
    private val playPosition: Float,
    val repeatState: RepeatState,
    val shuffleState: ShuffleState,
    val playerName: String,
    val connected: Boolean,
    val powered: Boolean,
    val currentVolume: Int?,
    val muted: Boolean?,
    val syncMaster: PlayerId?,
    val syncSlaves: List<PlayerId>?,
    private val timestamp: Instant
) {
    @Serializable
    enum class PlayState {
        @SerialName("play")
        Playing,

        @SerialName("pause")
        Paused,

        @SerialName("stop")
        Stopped
    }

    @Serializable
    enum class RepeatState {
        @SerialName("0")
        Off,

        @SerialName("1")
        RepeatTitle,

        @SerialName("2")
        RepeatAll
    }

    @Serializable
    enum class ShuffleState {
        @SerialName("0")
        Off,

        @SerialName("1")
        ShuffleSong,

        @SerialName("2")
        ShuffleAlbum
    }

    data class PlaylistInfo(
        val nowPlaying: Playlist.PlaylistItem?,
        val currentPosition: Int,
        val trackCount: Int,
        val lastChange: Instant
    )

    val playbackStartTimestamp get() = if (playbackState == PlayState.Playing) {
        timestamp.minus(playPosition.toDouble().toDuration(DurationUnit.SECONDS))
    } else {
        null
    }

    val currentPlayPosition get() = if (playbackState == PlayState.Playing) {
        playbackStartTimestamp?.let { Clock.System.now().minus(it) }
    } else {
        playPosition.toDouble().toDuration(DurationUnit.SECONDS)
    }
}
