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

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.extfuncs.viewModelWithParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.itemlist.BaseSlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowsePagedItemListOrGrid
import kotlin.getValue

class LibrarySearchResultsFragment : BaseSlimBrowseItemListFragment() {
    private val viewModel by viewModelWithParams {
        val args = requireArguments()
        LibrarySearchResultsViewModel(
            requireActivity().application,
            args.getParcelable("playerId", PlayerId::class),
            args.getString("query")!!,
            args.getParcelable("type", LibrarySearchRequest.Mode::class)
        )
    }

    override val baseViewModel get() = viewModel

    @Composable
    override fun createListContent(
        itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit,
        contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit
    ) {
        LibrarySearchResultsItemList(
            viewModel,
            prefs.serverConfig,
            itemSelectionListener = itemSelectionListener,
            contextMenuClickListener = contextMenuClickListener
        )
    }

    companion object {
        fun create(playerId: PlayerId, type: LibrarySearchRequest.Mode, searchTerm: String) =
            LibrarySearchResultsFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("playerId", playerId)
                    putParcelable("type", type)
                    putString("query", searchTerm)
                }
            }
    }
}

@Composable
fun LibrarySearchResultsItemList(
    viewModel: LibrarySearchResultsViewModel = viewModel(),
    serverConfig: ServerConfiguration?,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries = viewModel.itemsFlow.collectAsLazyPagingItems()
    val busyItem by viewModel.busyItemFlow.collectAsState()

    SlimBrowsePagedItemListOrGrid(
        entries,
        busyItem,
        serverConfig,
        viewModel.useGrid,
        true,
        itemSelectionListener,
        contextMenuClickListener
    )
}