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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.ServerConfiguration

@Composable
fun JiveHomeItemListOrGrid(
    viewModel: JiveHomeItemListViewModel,
    serverConfig: ServerConfiguration?,
    contentPadding: PaddingValues,
    mode: SlimBrowseItemListMode,
    modifier: Modifier = Modifier,
    onItemSelected: (JiveHomeMenuItem) -> Unit = {}
) {
    val menuEntries by viewModel.homeMenuItemsFlow.collectAsStateWithLifecycle(emptyList())

    when (mode) {
        is SlimBrowseItemListMode.List -> {
            LazyColumn(
                state = mode.listState,
                modifier = modifier,
                contentPadding = contentPadding
            ) {
                items(menuEntries) { entry ->
                    JiveUiItemRow(
                        item = entry.uiItem,
                        serverConfig = serverConfig,
                        showIcon = mode.showIcons,
                        busy = false,
                        modifier = Modifier.clickable { onItemSelected(entry.source) }
                    )
                }
            }
        }

        is SlimBrowseItemListMode.Grid -> {
            LazyVerticalGrid(
                state = mode.gridState,
                columns = GridCells.Adaptive(mode.cellMinSize),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
            ) {
                items(menuEntries) { entry ->
                    JiveUiItemGridCell(
                        item = entry.uiItem,
                        serverConfig = serverConfig,
                        busy = false,
                        modifier = Modifier.clickable { onItemSelected(entry.source) }
                    )
                }
            }
        }
    }
}
