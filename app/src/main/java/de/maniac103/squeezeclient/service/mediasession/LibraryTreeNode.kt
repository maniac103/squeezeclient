/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
 *
 * Portions adapted from SqueezeDrive, Copyright (c) 2026 SqueezeDrive contributors.

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

package de.maniac103.squeezeclient.service.mediasession

import de.maniac103.squeezeclient.model.JiveAction
import kotlin.io.encoding.Base64
import kotlin.text.removePrefix
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface LibraryTreeNode {
    val browsable: Boolean
    val playable: Boolean
    fun toMediaId(): String

    sealed class BrowseTreeNode(val id: String) : LibraryTreeNode {
        override val browsable get() = true
        override val playable get() = false
        override fun toMediaId() = id
    }

    data object Root : BrowseTreeNode("sc:root")

    data object Favorites : BrowseTreeNode("sc:favorites")

    data object RandomPlay : BrowseTreeNode("sc:random")

    data object Library : BrowseTreeNode("sc:library")

    data object Radio : BrowseTreeNode("sc:radio")

    @Serializable
    data class HomeMenuItem(val id: String, val title: String) : LibraryTreeNode {
        override val browsable get() = true
        override val playable get() = false
        override fun toMediaId() = encodeToId(PREFIX)
        companion object {
            internal const val PREFIX = "sc:h:"
        }
    }

    @Serializable
    data class BrowseItem(
        val title: String,
        val go: JiveAction?,
        val play: JiveAction?,
        val add: JiveAction?,
        val insert: JiveAction?
    ) : LibraryTreeNode {
        override val browsable get() = go != null
        override val playable get() = go == null && play != null
        override fun toMediaId() = encodeToId(PREFIX)
        companion object {
            internal const val PREFIX = "sc:b:"
        }
    }
}

fun String.toTreeNode(): LibraryTreeNode? = when {
    this == LibraryTreeNode.Root.id -> LibraryTreeNode.Root

    this == LibraryTreeNode.Favorites.id -> LibraryTreeNode.Favorites

    this == LibraryTreeNode.Library.id -> LibraryTreeNode.Library

    this == LibraryTreeNode.RandomPlay.id -> LibraryTreeNode.RandomPlay

    this == LibraryTreeNode.Radio.id -> LibraryTreeNode.Radio

    startsWith(LibraryTreeNode.HomeMenuItem.PREFIX) ->
        decodeToNode<LibraryTreeNode.HomeMenuItem>(LibraryTreeNode.HomeMenuItem.PREFIX)

    startsWith(LibraryTreeNode.BrowseItem.PREFIX) ->
        decodeToNode<LibraryTreeNode.BrowseItem>(LibraryTreeNode.BrowseItem.PREFIX)

    else -> null
}

private inline fun <reified T : LibraryTreeNode> T.encodeToId(prefix: String): String {
    val dataString = Json.encodeToString(this)
    return prefix + Base64.encode(dataString.encodeToByteArray())
}

private inline fun <reified T> String.decodeToNode(prefix: String): T? {
    val result = runCatching {
        val jsonString = Base64.decode(removePrefix(prefix)).decodeToString()
        Json.decodeFromString<T>(jsonString)
    }
    return result.getOrNull()
}
