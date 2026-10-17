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

package de.maniac103.squeezeclient.ui.contextmenu

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import kotlin.collections.emptyList

@Composable
fun ContextMenuBottomSheetContent(
    viewModel: ContextMenuBottomSheetViewModel,
    serverConfig: ServerConfiguration?,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val items by viewModel.pageFlow.collectAsState(emptyList())
    val busyItem by viewModel.busyItemFlow.collectAsState()
    val backNavTitle by viewModel.backNavTitleFlow.collectAsState(null)

    ContextMenuBottomSheetContent(
        items = items,
        busyItem = busyItem,
        parentItem = viewModel.parentItem,
        serverConfig = serverConfig,
        backNavTitle = backNavTitle,
        onBackNavClick = viewModel::popPage,
        itemSelectionListener = { item ->
            val goAction = item.actions?.goAction
            if (goAction?.isContextMenu == true) {
                viewModel.pushPage(item, goAction)
            } else {
                itemSelectionListener(item)
            }
        }
    )
}

@Composable
fun ContextMenuBottomSheetContent(
    items: List<SlimBrowseItemList.SlimBrowseItem>,
    busyItem: SlimBrowseItemList.SlimBrowseItem?,
    parentItem: SlimBrowseItemList.SlimBrowseItem,
    backNavTitle: String?,
    serverConfig: ServerConfiguration?,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    onBackNavClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row {
            parentItem.extractIconUrl()?.let {
                ArtworkImage(
                    artwork = parentItem,
                    serverConfig = serverConfig,
                    usePlaceholder = false,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                        .size(40.dp)
                        .align(Alignment.CenterVertically)
                )
            }
            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text(
                    text = parentItem.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                parentItem.subText?.let { subText ->
                    Text(
                        text = subText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
        backNavTitle?.let { backNavTitle ->
            Row {
                IconButton(
                    onClick = onBackNavClick,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_chevron_left_24dp),
                        contentDescription = null // FIXME
                    )
                }
                Text(
                    text = backNavTitle,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        LazyColumn {
            items(items) { item ->
                ContextMenuListRow(
                    title = item.title,
                    busy = item == busyItem,
                    selectable = item.actions?.goAction != null,
                    modifier = Modifier
                        .clickable(onClick = { itemSelectionListener(item) })
                )
            }
        }
    }
}
