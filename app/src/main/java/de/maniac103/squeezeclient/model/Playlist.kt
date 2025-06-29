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
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.parcelize.Parcelize

@OptIn(ExperimentalTime::class)
data class Playlist(
    override val items: List<PlaylistItem>,
    override val offset: Int,
    override val totalCount: Int,
    val currentIndex: Int,
    val timestamp: Instant
) : ListResponse<Playlist.PlaylistItem> {
    @Parcelize
    data class PlaylistItem(
        val title: String,
        val artist: String,
        val album: String,
        val actions: JiveActions,
        override val iconId: String? = null,
        override val icon: String? = null
    ) : Parcelable,
        ArtworkItem {
        fun asSlimbrowseItem() = SlimBrowseItemList.SlimBrowseItem(
            listPosition = 0,
            title = title,
            subText = null,
            extraInfo = null,
            textKey = null,
            type = null,
            trackType = null,
            icon = icon,
            iconId = iconId,
            actions = actions,
            nextWindow = null,
            subItems = null,
            webLink = null
        )
    }
}
