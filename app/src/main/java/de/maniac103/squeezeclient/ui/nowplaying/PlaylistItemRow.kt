/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

package de.maniac103.squeezeclient.ui.nowplaying

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton

@Composable
internal fun PlaylistItemRow(
    item: Playlist.PlaylistItem,
    shapes: ListItemShapes,
    colors: ListItemColors,
    isSelected: Boolean,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val subtext = when {
        item.artist.isEmpty() -> item.album
        item.album.isEmpty() -> item.artist
        else -> "${item.artist} · ${item.album}"
    }

    SegmentedListItem(
        shapes = shapes,
        colors = colors,
        selected = isSelected,
        onClick = onClick,
        supportingContent = {
            subtext.takeIf { it.isNotEmpty() }?.let { Text(it) }
        },
        leadingContent = {
            ArtworkImage(
                artwork = item,
                serverConfig = serverConfig,
                modifier = Modifier
                    .size(40.dp)
            )
        },
        trailingContent = {
            ResourceIconButton(
                drawableResId = R.drawable.ic_drag_handle_24dp,
                contentDescriptionResId = R.string.content_description_drag_playlist_item,
                onClick = {},
                modifier = dragHandleModifier
            )
        },
        modifier = modifier
    ) {
        Text(item.title)
    }
}

@Preview
@Composable
fun PlaylistItemRowPreview() {
    val item = Playlist.PlaylistItem(
        "Song title",
        "Artist",
        "Album",
        JiveActions.EMPTY
    )
    PlaylistItemRow(
        item = item,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = ListItemDefaults.segmentedColors(),
        isSelected = false,
        serverConfig = null
    )
}
