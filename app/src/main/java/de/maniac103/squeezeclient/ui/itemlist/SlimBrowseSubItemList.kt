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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job

@Composable
fun SlimBrowseSubItemList(
    viewModel: SlimBrowseSubItemListViewModel,
    serverConfig: ServerConfiguration?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job? = { null },
    onItemContextMenuSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job? = { null }
) {
    val entries by viewModel.itemsFlow.collectAsStateWithLifecycle(emptyList())
    val busyItem = viewModel.busyItemFlow.collectAsStateWithLifecycle()

    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        items(entries) { entry ->
            JiveUiItemRow(
                item = entry.uiItem,
                serverConfig = serverConfig,
                showIcon = false,
                busy = entry.source == busyItem,
                modifier = Modifier.clickable(
                    onClick = { viewModel.handleItemSelection(entry.source, onItemSelected) }
                ),
                onContextMenuSelected = {
                    viewModel.handleItemSelection(entry.source, onItemContextMenuSelected)
                }
            )
        }
    }
}
