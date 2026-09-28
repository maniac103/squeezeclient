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

package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.placeholder
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.Theme

@Composable
fun SlimBrowseItemGridCell(
    item: SlimBrowseItemList.SlimBrowseItem,
    serverConfig: ServerConfiguration?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val iconRequest = item.buildImageRequest(LocalContext.current, serverConfig)

    return SlimBrowseItemGridCell(
        title = item.title,
        subtext = item.subText ?: item.extraInfo,
        trailingWidget = item.extractTrailingWidget(contextMenuClickListener),
        iconRequest = iconRequest,
        busy = busy,
        modifier = modifier
    )
}

@Composable
fun SlimBrowseItemGridCell(
    title: String,
    subtext: String?,
    trailingWidget: SlimBrowseItemTrailingWidget?,
    iconRequest: ImageRequest,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(8.dp)
    ) {
        AsyncImage(
            iconRequest,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1F)
                .clip(RoundedCornerShape(8.dp))
        )

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1F)
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Theme.TextStyles.listItemPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )

                if (!subtext.isNullOrEmpty()) {
                    Text(
                        text = subtext,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = Theme.TextStyles.listItemSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            val extraModifier = Modifier
                .align(Alignment.CenterVertically)

            when {
                busy -> {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = extraModifier.size(24.dp)
                    )
                }

                trailingWidget is SlimBrowseItemTrailingWidget.Choice -> {
                    Text(
                        text = trailingWidget.label,
                        style = Theme.TextStyles.listItemSecondary,
                        modifier = extraModifier
                    )
                }

                trailingWidget is SlimBrowseItemTrailingWidget.Checkbox -> {
                    Checkbox(
                        checked = trailingWidget.checked,
                        onCheckedChange = { },
                        modifier = extraModifier
                    )
                }

                trailingWidget is SlimBrowseItemTrailingWidget.Radio -> {
                    RadioButton(
                        selected = trailingWidget.checked,
                        onClick = { },
                        modifier = extraModifier
                    )
                }

                trailingWidget is SlimBrowseItemTrailingWidget.ContextMenu -> {
                    Image(
                        painterResource(R.drawable.ic_menu_overflow),
                        contentScale = ContentScale.Inside,
                        contentDescription = null, // FIXME
                        modifier = extraModifier
                            .size(48.dp)
                            .clickable(
                                onClick = trailingWidget.clickListener,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false)
                            )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SlimBrowseItemGridCellPreview() {
    data class Item(
        val title: String,
        val subtext: String?,
        val trailingWidget: SlimBrowseItemTrailingWidget?,
        val busy: Boolean
    )

    val items = listOf(
        Item("Title", "Subtext", null, false),
        Item("No Subtext", null, null, false),
        Item("No Subtext", null, SlimBrowseItemTrailingWidget.ContextMenu {}, false),
        Item("Busy", "Subtext", null, true),
        Item("Menu", "Subtext", SlimBrowseItemTrailingWidget.ContextMenu {}, false),
        Item("Choice", "Subtext", SlimBrowseItemTrailingWidget.Choice("On"), false),
        Item("Radio", "Subtext", SlimBrowseItemTrailingWidget.Radio(true), false),
        Item("Checkbox", "Subtext", SlimBrowseItemTrailingWidget.Checkbox(true), false)
    )
    val dummyRequest = ImageRequest.Builder(LocalContext.current)
        .data(null)
        .placeholder(R.drawable.ic_disc_24dp)
        .build()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            SlimBrowseItemGridCell(
                item.title,
                item.subtext,
                item.trailingWidget,
                dummyRequest,
                item.busy
            )
        }
    }
}