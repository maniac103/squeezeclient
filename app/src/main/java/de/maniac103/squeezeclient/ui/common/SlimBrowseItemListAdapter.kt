/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.progressindicator.CircularProgressIndicator
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.GridItemSlimbrowseBinding
import de.maniac103.squeezeclient.databinding.ListItemSlimbrowseBinding
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import me.zhanghai.android.fastscroll.PopupTextProvider

class SlimBrowseItemListAdapter(
    diffCb: DiffUtil.ItemCallback<SlimBrowseItemList.SlimBrowseItem>,
    private val showIcons: Boolean,
    private val isGrid: Boolean
) : PagingDataAdapter<SlimBrowseItemList.SlimBrowseItem, SlimBrowseItemListViewHolder>(diffCb),
    PopupTextProvider {
    interface ItemSelectionListener {
        fun onItemSelected(item: SlimBrowseItemList.SlimBrowseItem): Job?
        fun onContextMenu(item: SlimBrowseItemList.SlimBrowseItem): Job?
        fun onCheckBoxChanged(item: SlimBrowseItemList.SlimBrowseItem, checked: Boolean): Job?
        fun onRadioChecked(item: SlimBrowseItemList.SlimBrowseItem): Job?
    }
    data class ItemBinding(
        val root: View,
        val icon: ImageView,
        val title: TextView,
        val subText: TextView,
        val contextContainer: ViewGroup,
        val loadingIndicator: CircularProgressIndicator
    )

    var itemSelectionListener: ItemSelectionListener? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SlimBrowseItemListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isGrid) {
            val original = GridItemSlimbrowseBinding.inflate(inflater, parent, false)
            ItemBinding(
                original.root,
                original.icon,
                original.title,
                original.subtext,
                original.contextContainer,
                original.loadingIndicator
            )
        } else {
            val original = ListItemSlimbrowseBinding.inflate(inflater, parent, false)
            ItemBinding(
                original.root,
                original.icon,
                original.title,
                original.subtext,
                original.contextContainer,
                original.loadingIndicator
            )
        }
        val contextLayoutResource = when (viewType) {
            VIEW_TYPE_CHECKBOX -> R.layout.list_item_extension_checkbox
            VIEW_TYPE_RADIO -> R.layout.list_item_extension_radio
            VIEW_TYPE_CONTEXT -> R.layout.list_item_extension_contextmenu
            VIEW_TYPE_CHOICES -> R.layout.list_item_extension_choices
            else -> null
        }
        contextLayoutResource?.let { inflater.inflate(it, binding.contextContainer) }

        val holder = SlimBrowseItemListViewHolder(binding)
        binding.root.setOnClickListener {
            val item = getItem(holder.bindingAdapterPosition) ?: return@setOnClickListener
            itemSelectionListener?.onItemSelected(item)?.let { holder.setupBusyListener(it) }
        }

        holder.contextMenu?.setOnClickListener {
            val item = getItem(holder.bindingAdapterPosition) ?: return@setOnClickListener
            itemSelectionListener?.onContextMenu(item)?.let { holder.setupBusyListener(it) }
        }
        holder.radio?.setOnCheckedChangeListener { _, checked ->
            val item = getItem(holder.bindingAdapterPosition)
            if (item != null && !holder.isBinding && checked) {
                itemSelectionListener?.onRadioChecked(item)?.let { holder.setupBusyListener(it) }
            }
        }
        holder.checkbox?.setOnCheckedChangeListener { _, checked ->
            val item = getItem(holder.bindingAdapterPosition)
            if (item != null && !holder.isBinding) {
                itemSelectionListener?.onCheckBoxChanged(item, checked)?.let {
                    holder.setupBusyListener(it)
                }
            }
        }

        binding.icon.isVisible = showIcons

        return holder
    }

    override fun onBindViewHolder(holder: SlimBrowseItemListViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item?.actions?.checkbox != null -> VIEW_TYPE_CHECKBOX
            item?.actions?.radio != null -> VIEW_TYPE_RADIO
            item?.actions?.choices != null -> VIEW_TYPE_CHOICES
            item?.actions?.hasContextMenu == true -> VIEW_TYPE_CONTEXT
            else -> VIEW_TYPE_NOCONTEXT
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence =
        getItem(position)?.textKey ?: ""

    companion object {
        private const val VIEW_TYPE_NOCONTEXT = 0
        private const val VIEW_TYPE_CONTEXT = 1
        private const val VIEW_TYPE_RADIO = 2
        private const val VIEW_TYPE_CHECKBOX = 3
        private const val VIEW_TYPE_CHOICES = 4
    }
}
