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

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.LazyColumnScrollStateHelper
import de.maniac103.squeezeclient.ui.composables.LazyGridScrollStateHelper

enum class SlimBrowseItemListMode {
    ListWithoutIcons,
    ListWithIcons,
    Grid
}

@Composable
fun SlimBrowsePagedItemListOrGrid(
    entries: LazyPagingItems<SlimBrowseItemList.SlimBrowseItem>,
    busyItem: SlimBrowseItemList.SlimBrowseItem?,
    serverConfig: ServerConfiguration?,
    mode: SlimBrowseItemListMode,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    if (!entries.loadState.isIdle) {
        Box {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
        return
    }

    // TODO: implement fast scroll

    if (mode == SlimBrowseItemListMode.Grid) {
        // FIXME: better pass value in mode
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        LazyGridScrollStateHelper { state ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isLandscape) 160.dp else 120.dp),
                state = state,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.itemCount) { index ->
                    entries[index]?.let { entry ->
                        SlimBrowseItemGridCell(
                            item = entry,
                            serverConfig = serverConfig,
                            busy = entry == busyItem,
                            modifier = Modifier.clickable(
                                onClick = { itemSelectionListener(entry) }
                            ),
                            contextMenuClickListener = contextMenuClickListener
                        )
                    }
                }
            }
        }
    } else {
        LazyColumnScrollStateHelper { state ->
            LazyColumn(
                state = state,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(entries.itemCount) { index ->
                    entries[index]?.let { entry ->
                        SlimBrowseItemListRow(
                            item = entry,
                            serverConfig = serverConfig,
                            showIcon = mode == SlimBrowseItemListMode.ListWithIcons,
                            busy = entry == busyItem,
                            modifier = Modifier.clickable(
                                onClick = { itemSelectionListener(entry) }
                            ),
                            contextMenuClickListener = contextMenuClickListener
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlimBrowseItemList(
    viewModel: SlimBrowseItemListViewModel = viewModel(),
    serverConfig: ServerConfiguration?,
    mode: SlimBrowseItemListMode,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries = viewModel.itemsFlow.collectAsLazyPagingItems()
    val busyItem by viewModel.busyItemFlow.collectAsState()

    SlimBrowsePagedItemListOrGrid(
        entries,
        busyItem,
        serverConfig,
        mode,
        itemSelectionListener,
        contextMenuClickListener
    )
}

@Composable
fun SlimBrowseSubItemList(
    viewModel: SlimBrowseSubItemListViewModel = viewModel(),
    serverConfig: ServerConfiguration?,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries by viewModel.itemsFlow.collectAsState(emptyList())
    val busyItem = viewModel.busyItemFlow.collectAsState()

    LazyColumnScrollStateHelper { state ->
        LazyColumn(
            state = state,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(entries) { entry ->
                SlimBrowseItemListRow(
                    item = entry,
                    serverConfig = serverConfig,
                    showIcon = false,
                    busy = entry == busyItem,
                    modifier = Modifier.clickable(
                        onClick = { itemSelectionListener(entry) }
                    ),
                    contextMenuClickListener = contextMenuClickListener
                )
            }
        }
    }
}