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

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlin.getValue

class SlimBrowseSubItemListFragment : BaseSlimBrowseItemListFragment() {
    private val viewModel: SlimBrowseSubItemListViewModel by viewModels {
        SavedStateViewModelFactory(requireActivity().application, this, requireArguments())
    }

    override val baseViewModel get() = viewModel

    @Composable
    override fun createContent(
        itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit,
        contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit
    ) {
        SlimBrowseSubItemList(
            viewModel,
            prefs.serverConfig,
            itemSelectionListener = itemSelectionListener,
            contextMenuClickListener = contextMenuClickListener
        )
    }

    companion object {
        fun create(playerId: PlayerId, title: String, fetchAction: JiveAction, listPosition: Int) =
            SlimBrowseSubItemListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("playerId", playerId)
                    putString("title", title)
                    putParcelable("fetchAction", fetchAction)
                    putInt("listPosition", listPosition)
                }
            }
    }
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

    LazyColumn(
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(entries) { entry ->
            SlimBrowseItemListEntry(
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