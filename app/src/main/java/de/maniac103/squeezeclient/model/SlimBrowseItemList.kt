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
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Parcelize
data class SlimBrowseItemList(
    override val items: List<SlimBrowseItem>,
    override val offset: Int,
    override val totalCount: Int,
    val title: String?,
    val window: Window? = null
) : ListResponse<SlimBrowseItemList.SlimBrowseItem>,
    Parcelable {
    @Parcelize
    data class SlimBrowseItem(
        val listPosition: Int,
        val title: String,
        val subText: String?,
        val extraInfo: String?,
        val textKey: String?,
        val type: ItemType?,
        val trackType: TrackType?,
        override val icon: String?,
        override val iconId: String?,
        val actions: JiveActions?,
        val nextWindow: NextWindow?,
        val subItems: List<SlimBrowseItem>?,
        val webLink: String?
    ) : Parcelable,
        ArtworkItem

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("unused")
    enum class NextWindow {
        @SerialName("home")
        Home,

        @JsonNames("parent", "parentNoRefresh")
        Parent,

        @SerialName("grandparent")
        GrandParent,

        @SerialName("nowPlaying")
        NowPlaying,

        @SerialName("refresh")
        RefreshSelf,

        @SerialName("myMusic")
        MyMusic,

        @SerialName("refreshOrigin")
        ParentWithRefresh,

        @SerialName("presets")
        Presets
    }

    @Serializable
    @Suppress("unused")
    enum class ItemType {
        @SerialName("text")
        Text,

        @SerialName("audio")
        Audio,

        @SerialName("playlist")
        Playlist,

        @SerialName("outline")
        Outline,

        @SerialName("opml")
        Opml,

        @SerialName("redirect")
        Redirection,

        @SerialName("slideshow")
        Slideshow,

        @SerialName("link")
        Link,

        @SerialName("url")
        Url,

        @SerialName("search")
        Search
    }

    @Serializable
    @Suppress("unused")
    enum class TrackType {
        @SerialName("local")
        Local,

        @SerialName("radio")
        Radio
    }

    @Serializable
    @Parcelize
    data class Window(
        val windowStyle: WindowStyle? = null,
        @SerialName("textarea")
        val textArea: String? = null
    ) : Parcelable
}
