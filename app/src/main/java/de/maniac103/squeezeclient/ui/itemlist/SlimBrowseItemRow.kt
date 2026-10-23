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

package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.ArtworkImage

@Composable
fun SlimBrowseItemRow(
    item: SlimBrowseItemList.SlimBrowseItem,
    serverConfig: ServerConfiguration?,
    showIcon: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) = SlimBrowseItemRow(
    title = item.title,
    subtext = item.subText ?: item.extraInfo,
    trailingWidget = item.extractTrailingWidget(contextMenuClickListener),
    artwork = if (showIcon) item else null,
    serverConfig = serverConfig,
    busy = busy,
    modifier = modifier
)

@Composable
fun SlimBrowseItemRow(
    title: String,
    subtext: String?,
    trailingWidget: SlimBrowseItemTrailingWidget?,
    artwork: ArtworkItem?,
    serverConfig: ServerConfiguration?,
    busy: Boolean,
    modifier: Modifier = Modifier
) = ListItem(
    leadingContent = {
        if (artwork != null) {
            ArtworkImage(
                artwork = artwork,
                serverConfig = serverConfig,
                modifier = Modifier
                    .size(40.dp)
            )
        }
    },
    trailingContent = {
        SlimBrowseItemTrailingWidgetUi(
            busy = busy,
            widget = trailingWidget
        )
    },
    supportingContent = {
        if (!subtext.isNullOrEmpty()) {
            Text(
                text = subtext,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    modifier = modifier
) {
    Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Preview
@Composable
fun SlimBrowseItemListRowPreview() {
    val dummyArtwork = object : ArtworkItem {
        override val icon = null
        override val iconId = null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SlimBrowseItemRow("Title", "Subtext", null, dummyArtwork, null, false)
        SlimBrowseItemRow("No Subtext", null, null, dummyArtwork, null, false)
        SlimBrowseItemRow("No icon", null, null, null, null, false)
        SlimBrowseItemRow("Busy", "Subtext", null, dummyArtwork, null, true)
        SlimBrowseItemRow(
            "Menu",
            "Subtext",
            SlimBrowseItemTrailingWidget.ContextMenu {},
            dummyArtwork,
            null,
            false
        )
        SlimBrowseItemRow(
            "Choice",
            "Subtext",
            SlimBrowseItemTrailingWidget.Choice("On"),
            dummyArtwork,
            null,
            false
        )
        SlimBrowseItemRow(
            "Radio",
            "Subtext",
            SlimBrowseItemTrailingWidget.Radio(true),
            dummyArtwork,
            null,
            false
        )
        SlimBrowseItemRow(
            "Checkbox",
            "Subtext",
            SlimBrowseItemTrailingWidget.Checkbox(true),
            dummyArtwork,
            null,
            false
        )
    }
}
