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

package de.maniac103.squeezeclient.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListMode
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListOrGrid

@Composable
fun RadioSearchResultsItemList(
    viewModel: RadioSearchResultsViewModel,
    serverConfig: ServerConfiguration?,
    contentPadding: PaddingValues,
    mode: SlimBrowseItemListMode,
    modifier: Modifier = Modifier,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries = viewModel.itemsFlow.collectAsLazyPagingItems()
    val busyItem by viewModel.busyItemFlow.collectAsStateWithLifecycle()

    SlimBrowseItemListOrGrid(
        entries = entries,
        busyItem = busyItem,
        serverConfig = serverConfig,
        contentPadding = contentPadding,
        mode = mode,
        modifier = modifier,
        itemSelectionListener = itemSelectionListener,
        contextMenuClickListener = contextMenuClickListener
    )
}
