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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.model.ServerConfiguration

@Composable
fun JiveUiItemGridCell(
    item: JiveUiItem,
    serverConfig: ServerConfiguration?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onContextMenuSelected: () -> Unit = {}
) {
    val colors = ListItemDefaults.colors()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(modifier)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(8.dp)
    ) {
        if (item.icon != null) {
            CompositionLocalProvider(LocalContentColor provides colors.leadingContentColor) {
                JiveUiItemRowIcon(
                    icon = item.icon,
                    serverConfig = serverConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1F)
                )
            }
        }

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
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.contentColor,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )

                if (!item.subText.isNullOrEmpty()) {
                    Text(
                        text = item.subText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.supportingContentColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            CompositionLocalProvider(LocalContentColor provides colors.trailingContentColor) {
                val trailingWidgetModifier = Modifier.align(Alignment.CenterVertically)
                if (busy) {
                    JiveUiItemRowBusyIndicator(modifier = trailingWidgetModifier)
                } else if (item.trailingWidget != null) {
                    JiveUiItemRowTrailingWidget(
                        widget = item.trailingWidget,
                        onContextMenuSelected = onContextMenuSelected,
                        modifier = trailingWidgetModifier
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SlimBrowseItemGridCellPreview() {
    val items = generateJiveUiPreviewWidgets()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { (item, busy) ->
            JiveUiItemGridCell(
                item = item,
                serverConfig = null,
                busy = busy
            )
        }
    }
}
