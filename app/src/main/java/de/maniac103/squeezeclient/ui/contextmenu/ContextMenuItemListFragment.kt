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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.databinding.FragmentContextMenuListBinding
import de.maniac103.squeezeclient.databinding.ListItemContextMenuBinding
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.getParcelableList
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.common.BasePrepopulatedListAdapter
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlinx.coroutines.Job

class ContextMenuItemListFragment :
    ViewBindingFragment<FragmentContextMenuListBinding>(
        FragmentContextMenuListBinding::inflate
    ) {
    fun interface ItemClickListener {
        fun onItemClicked(item: SlimBrowseItemList.SlimBrowseItem): Job?
    }

    val parent get() =
        requireArguments().getParcelable("parent", SlimBrowseItemList.SlimBrowseItem::class)
    private val items get() =
        requireArguments().getParcelableList("items", SlimBrowseItemList.SlimBrowseItem::class)
    private val listener get() = requireParentAs<ItemClickListener>()

    override fun onBindingCreated(binding: FragmentContextMenuListBinding) {
        val itemAdapter = ItemAdapter(items).apply {
            itemSelectionListener = BasePrepopulatedListAdapter.ItemSelectionListener { item ->
                listener.onItemClicked(item)
            }
        }

        binding.items.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = itemAdapter
        }
    }

    private class ItemAdapter(items: List<SlimBrowseItemList.SlimBrowseItem>) :
        BasePrepopulatedListAdapter<SlimBrowseItemList.SlimBrowseItem, ItemViewHolder>(items) {
        override fun onCreateViewHolder(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): ItemViewHolder {
            val binding = ListItemContextMenuBinding.inflate(inflater, parent, false)
            return ItemViewHolder(binding)
        }

        override fun onBindViewHolder(
            holder: ItemViewHolder,
            item: SlimBrowseItemList.SlimBrowseItem
        ) {
            holder.binding.root.text = item.title
            // FIXME: have isSelectable property?
            holder.binding.root.isEnabled = item.actions?.goAction != null
        }
    }

    private class ItemViewHolder(val binding: ListItemContextMenuBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        fun create(
            parent: SlimBrowseItemList.SlimBrowseItem,
            items: List<SlimBrowseItemList.SlimBrowseItem>
        ) = ContextMenuItemListFragment().apply {
            arguments = bundleOf("parent" to parent, "items" to ArrayList(items))
        }
    }
}
