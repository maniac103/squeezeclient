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
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.ScrollIndicator
import kotlin.time.Duration.Companion.seconds
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

sealed interface SlimBrowseItemListMode {
    val scrollIndicatorState: ScrollIndicatorState?

    data class List(val listState: LazyListState, val showIcons: Boolean) : SlimBrowseItemListMode {
        override val scrollIndicatorState get() = listState.scrollIndicatorState
    }

    data class Grid(val gridState: LazyGridState, val cellMinSize: Dp) : SlimBrowseItemListMode {
        override val scrollIndicatorState get() = gridState.scrollIndicatorState
    }
}

@Composable
fun rememberSlimBrowseItemListMode(
    useGrid: Boolean,
    showIcons: Boolean,
    gridCellMinSize: Dp,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
): SlimBrowseItemListMode {
    val lastUsedGrid = remember { mutableStateOf(useGrid) }

    SideEffect {
        if (lastUsedGrid.value != useGrid) {
            if (useGrid) {
                gridState.requestScrollToItem(listState.firstVisibleItemIndex)
            } else {
                listState.requestScrollToItem(gridState.firstVisibleItemIndex)
            }
            lastUsedGrid.value = useGrid
        }
    }

    return remember(useGrid, showIcons, listState, gridState) {
        if (useGrid) {
            SlimBrowseItemListMode.Grid(gridState, gridCellMinSize)
        } else {
            SlimBrowseItemListMode.List(listState, showIcons)
        }
    }
}

@Composable
fun SlimBrowsePagedItemListOrGrid(
    entries: LazyPagingItems<SlimBrowseItemList.SlimBrowseItem>,
    busyItem: SlimBrowseItemList.SlimBrowseItem?,
    serverConfig: ServerConfiguration?,
    contentPadding: PaddingValues,
    mode: SlimBrowseItemListMode,
    modifier: Modifier = Modifier,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    if (!entries.loadState.isIdle) {
        Box {
            LinearProgressIndicator(
                modifier = modifier
                    .fillMaxWidth()
            )
        }
        return
    }

    val scrollbarSettings = ScrollbarSettings.Default.copy(
        thumbSelectedColor = MaterialTheme.colorScheme.primaryContainer,
        thumbUnselectedColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        hideDelayMillis = 2.seconds.inWholeMilliseconds.toInt()
    )

    when (mode) {
        is SlimBrowseItemListMode.Grid -> {
            LazyVerticalGridScrollbar(
                state = mode.gridState,
                settings = scrollbarSettings,
                indicatorContent = { index, isThumbSelected ->
                    ScrollIndicator(entries[index]?.textKey, isThumbSelected)
                }
            ) {
                LazyVerticalGrid(
                    state = mode.gridState,
                    columns = GridCells.Adaptive(mode.cellMinSize),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = modifier
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
        }

        is SlimBrowseItemListMode.List -> {
            LazyColumnScrollbar(
                state = mode.listState,
                settings = scrollbarSettings,
                indicatorContent = { index, isThumbSelected ->
                    ScrollIndicator(entries[index]?.textKey, isThumbSelected)
                }
            ) {
                LazyColumn(
                    state = mode.listState,
                    contentPadding = contentPadding,
                    modifier = modifier
                ) {
                    items(entries.itemCount) { index ->
                        entries[index]?.let { entry ->
                            SlimBrowseItemListRow(
                                item = entry,
                                serverConfig = serverConfig,
                                showIcon = mode.showIcons,
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
}

@Composable
fun SlimBrowseItemList(
    viewModel: SlimBrowseItemListViewModel,
    serverConfig: ServerConfiguration?,
    contentPadding: PaddingValues,
    mode: SlimBrowseItemListMode,
    modifier: Modifier = Modifier,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries = viewModel.itemsFlow.collectAsLazyPagingItems()
    val busyItem by viewModel.busyItemFlow.collectAsStateWithLifecycle()

    SlimBrowsePagedItemListOrGrid(
        entries,
        busyItem,
        serverConfig,
        contentPadding,
        mode,
        modifier,
        itemSelectionListener,
        contextMenuClickListener
    )
}

@Composable
fun SlimBrowseSubItemList(
    viewModel: SlimBrowseSubItemListViewModel,
    serverConfig: ServerConfiguration?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries by viewModel.itemsFlow.collectAsStateWithLifecycle(emptyList())
    val busyItem = viewModel.busyItemFlow.collectAsStateWithLifecycle()

    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        modifier = modifier
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
