/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.cometd.response

import androidx.core.net.toUri
import de.maniac103.squeezeclient.cometd.BooleanAsIntSerializer
import de.maniac103.squeezeclient.model.DownloadSongInfo
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class DownloadSongInfoListResponse(
    val count: Int,
    @SerialName("titles_loop")
    val titles: List<Title>
) {
    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class Title(
        val id: Long,
        @SerialName("album_id")
        val albumId: Long,
        val title: String,
        val album: String,
        @SerialName("tracknum")
        val trackNumber: Int,
        @SerialName("albumartist")
        val albumArtist: String? = null,
        @JsonNames("trackartist")
        val artist: String,
        @SerialName("artwork_track_id")
        val artworkTrackId: String,
        val duration: Double,
        @SerialName("samplerate")
        val sampleRate: Int,
        val url: String,
        @SerialName("remote")
        @Serializable(with = BooleanAsIntSerializer::class)
        val isRemote: Boolean
    )

    fun asModelItems(mediaDirectories: List<String>): List<DownloadSongInfo> {
        return titles.mapNotNull { title ->
            val fileUri = title.url.toUri()
            val fileUriPath = fileUri.path
            val relevantDir = fileUriPath?.let { path ->
                mediaDirectories.find { path.startsWith(it) }
            }
            val storagePath = if (relevantDir != null) {
                fileUriPath.substring(relevantDir.length).dropWhile { it == '/' }
            } else {
                fileUri.lastPathSegment
            } ?: return@mapNotNull null

            DownloadSongInfo(
                title.id,
                title.title,
                title.artist,
                title.album,
                title.albumArtist,
                storagePath,
                title.duration.seconds
            )
        }
    }

    companion object {
        // Information that will be requested about songs.
        // A 	<role> 	For every artist role (one of "artist", "composer", "conductor", "band", "albumartist" or "trackartist"), a comma and space (', ') separated list of names.
        // AA 	<role> 	This is like "A", but without the space after the comma. This should simplify parsing/splitting when required.
        // B 	buttons A hash with button definitions. Only available for certain plugins such as Pandora.
        // d 	duration Song duration in seconds.
        // e 	album_id 	Album ID. Only if known.
        // j 	coverart 	1 if coverart is available for this song. Not listed otherwise.
        // J 	artwork_track_id 	Identifier of the album track used by the server to display the album's artwork. Not listed if artwork is not available for this album.
        // K 	artwork_url 	A full URL to remote artwork. Only available for certain online music services.
        // l 	album 	Album name. Only if known.
        // r 	bitrate 	Song bitrate. Only if known.
        // S 	<role>_ids 	For each role as defined above, the list of ids (comma separated).
        // t 	tracknum 	Track number. Only if known.
        // T 	samplerate 	Song sample rate (in KHz)
        // u 	url 	Song file url.
        // x 	remote 	If 1, this is a remote track.
        const val SERVER_TAGS = "AdeJltTux"
    }
}
