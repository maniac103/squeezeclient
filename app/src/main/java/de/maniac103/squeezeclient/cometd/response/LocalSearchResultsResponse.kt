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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocalSearchResultsResponse(
    @SerialName("albums_count")
    val albumCount: Int = 0,
    @SerialName("albums_loop")
    val albums: List<AlbumResponse>? = null,
    @SerialName("contributors_count")
    val artistCount: Int = 0,
    @SerialName("contributors_loop")
    val artists: List<ArtistResponse>? = null,
    @SerialName("genres_count")
    val genreCount: Int = 0,
    @SerialName("genres_loop")
    val genres: List<GenreResponse>? = null,
    @SerialName("tracks_count")
    val trackCount: Int = 0,
    @SerialName("tracks_loop")
    val tracks: List<TrackResponse>? = null,
    @SerialName("count")
    val totalCount: Int
) {
    interface ItemResponse {
        val title: String
        val id: Long
    }

    @Serializable
    data class AlbumResponse(
        @SerialName("album")
        override val title: String,
        @SerialName("album_id")
        override val id: Long
    ) : ItemResponse

    @Serializable
    data class ArtistResponse(
        @SerialName("contributor")
        override val title: String,
        @SerialName("contributor_id")
        override val id: Long
    ) : ItemResponse

    @Serializable
    data class GenreResponse(
        @SerialName("genre")
        override val title: String,
        @SerialName("genre_id")
        override val id: Long
    ) : ItemResponse

    @Serializable
    data class TrackResponse(
        @SerialName("track")
        override val title: String,
        @SerialName("track_id")
        override val id: Long
    ) : ItemResponse
}
