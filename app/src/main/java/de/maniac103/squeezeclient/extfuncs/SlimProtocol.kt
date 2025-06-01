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

package de.maniac103.squeezeclient.extfuncs

import de.maniac103.squeezeclient.model.JiveAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

fun JiveAction.serverMightCacheResults() = cmd.getOrNull(0) == "artistinfo"

fun JiveAction.fetchesAlbumList() =
    cmd == listOf("browselibrary", "items") && params["mode"] == "albums"

fun JiveAction.fetchesAlbumTrackList() =
    cmd == listOf("browselibrary", "items") && params["mode"] == "tracks"

fun JsonObject.extractSlimBrowseAlbumIdForAlbumListResponse() =
    get("album_id")?.jsonPrimitive?.longOrNull

fun JsonObject.extractSlimBrowseTrackIdForTrackListResponse() =
    get("track_id")?.jsonPrimitive?.longOrNull
