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

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import de.maniac103.squeezeclient.databinding.FragmentComposeBinding
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlin.getValue
import kotlinx.coroutines.Job

class ContextMenuItemListFragment :
    ViewBindingFragment<FragmentComposeBinding>(
        FragmentComposeBinding::inflate
    ) {
    fun interface ItemClickListener {
        fun onItemClicked(item: SlimBrowseItemList.SlimBrowseItem): Job?
    }

    private val viewModel: ContextMenuItemListViewModel by viewModels {
        SavedStateViewModelFactory(requireActivity().application, this, requireArguments())
    }
    private val listener get() = requireParentAs<ItemClickListener>()
    val parent get() = viewModel.parent

    override fun onBindingCreated(binding: FragmentComposeBinding) {
        binding.compose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    ContextMenuItemList(
                        viewModel,
                        { item ->
                            val job = listener.onItemClicked(item)
                            job?.let { viewModel.setItemBusy(item, it) }
                        }
                    )
                }
            }
        }
    }

    companion object {
        fun create(
            parent: SlimBrowseItemList.SlimBrowseItem,
            items: List<SlimBrowseItemList.SlimBrowseItem>
        ) = ContextMenuItemListFragment().apply {
            arguments = Bundle().apply {
                putParcelable("parent", parent)
                putParcelableArrayList("items", ArrayList(items))
            }
        }
    }
}

@Composable
fun ContextMenuItemList(
    viewModel: ContextMenuItemListViewModel = viewModel(),
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit
) {
    val items by viewModel.itemsFlow.collectAsState(emptyList())
    val busyItem by viewModel.busyItemFlow.collectAsState()

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