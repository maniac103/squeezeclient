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

package de.maniac103.squeezeclient.extfuncs

import android.content.SharedPreferences
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val SharedPreferences.serverConfig: ServerConfiguration? get() {
    val name = getString("server_name", null)
    val hostnameAndPort = getString("server_url", null)
    return if (name != null && hostnameAndPort != null) {
        ServerConfiguration(
            name,
            hostnameAndPort,
            getString("user", null),
            getString("password", null)
        )
    } else {
        null
    }
}

val SharedPreferences.lastSelectedPlayer get() =
    getString("active_player", null)?.let { PlayerId(it) }

val SharedPreferences.fadeInDuration: Duration get() =
    getInt("fade_in_duration", 0).toDuration(DurationUnit.SECONDS)

enum class DownloadFolderStructure(val prefValue: String) {
    AsOnServer("server"),
    Album("album"),
    Artist("artist"),
    AlbumUnderArtist("albumunderartist"),
    ArtistAlbum("artistalbum")
}

val SharedPreferences.downloadFolderStructure: DownloadFolderStructure get() {
    val value = getString(
        "download_path_structure",
        DownloadFolderStructure.AlbumUnderArtist.prefValue
    )
    return DownloadFolderStructure.entries.find { value == it.prefValue }
        ?: throw IllegalStateException("Download folder structure value $value has no mapping")
}

val SharedPreferences.useVolumeButtonsForPlayerVolume: Boolean get() =
    getBoolean("use_volume_buttons", true)

fun SharedPreferences.Editor.putServerConfig(config: ServerConfiguration) {
    putString("server_url", config.hostnameAndPort)
    putString("server_name", config.name)
    if (config.username.isNullOrEmpty() || config.password.isNullOrEmpty()) {
        remove("user")
        remove("password")
    } else {
        putString("user", config.username)
        putString("password", config.password)
    }
}
fun SharedPreferences.Editor.putLastSelectedPlayer(playerId: PlayerId) {
    putString("active_player", playerId.id)
}
