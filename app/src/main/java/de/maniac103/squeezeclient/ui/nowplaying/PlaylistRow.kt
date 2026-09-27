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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.itemlist.buildImageRequest

@Composable
fun PlaylistRow(
    item: Playlist.PlaylistItem,
    isSelected: Boolean,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val subtext = when {
        item.artist.isEmpty() -> item.album
        item.album.isEmpty() -> item.artist
        else -> "${item.artist} · ${item.album}"
    }

    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = { subtext.takeIf { it.isNotEmpty() }?.let { Text(it) } },
        leadingContent = {
            AsyncImage(
                item.buildImageRequest(LocalContext.current, serverConfig),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        },
        trailingContent = {
            Image(
                painterResource(R.drawable.ic_drag_horizontal_24dp),
                contentScale = ContentScale.Inside,
                contentDescription = null, // FIXME
                modifier = dragHandleModifier
                    .size(48.dp)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            headlineColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                ListItemDefaults.colors().headlineColor
            },
            supportingColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                ListItemDefaults.colors().supportingTextColor
            }
        ),
        modifier = modifier
    )
}

@Preview
@Composable
fun PlaylistRowPreview() {
    val item = Playlist.PlaylistItem(
        "Song title",
        "Artist",
        "Album",
        JiveActions(null, null, null, null, null, null, null, null, null, null, null, null, null)
    )
    PlaylistRow(item, false, null)
}