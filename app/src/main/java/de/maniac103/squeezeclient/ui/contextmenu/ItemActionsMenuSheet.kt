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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.BottomSheetItemActionsBinding
import de.maniac103.squeezeclient.databinding.ListItemContextMenuBinding
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.common.BasePrepopulatedListAdapter
import kotlinx.coroutines.Job

class ItemActionsMenuSheet : BottomSheetDialogFragment() {
    interface Listener {
        fun onActionSelected(action: JiveAction): Job?
        fun onDownloadSelected(data: DownloadRequestData): Job?
    }

    private val item get() =
        requireArguments().getParcelable("item", SlimBrowseItemList.SlimBrowseItem::class)

    private lateinit var binding: BottomSheetItemActionsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetItemActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val item = item
        binding.icon.apply {
            val iconUrl = item.extractIconUrl(requireContext())
            isVisible = iconUrl != null
            load(iconUrl)
        }
        binding.title.text = item.title
        binding.subtext.apply {
            isVisible = !item.subText.isNullOrEmpty()
            text = item.subText
        }

        val actionItems = requireNotNull(item.actions).let { actions ->
            listOfNotNull(
                actions.addAction?.let { ActionItem(R.string.action_add, it, null) },
                actions.insertAction?.let { ActionItem(R.string.action_insert, it, null) },
                actions.playAction?.let { ActionItem(R.string.action_play, it, null) },
                actions.downloadData?.let { ActionItem(R.string.action_download, null, it) }
            )
        }

        val itemAdapter = ItemAdapter(actionItems).apply {
            itemSelectionListener =
                BasePrepopulatedListAdapter.ItemSelectionListener { actionItem ->
                    val listener = parentFragment as? Listener
                    val job = if (actionItem.download != null) {
                        listener?.onDownloadSelected(actionItem.download)
                    } else {
                        listener?.onActionSelected(requireNotNull(actionItem.action))
                    }
                    job?.invokeOnCompletion { dismissAllowingStateLoss() }
                    job
                }
        }

        binding.items.apply {
            layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false)
            adapter = itemAdapter
        }
    }

    private class ItemAdapter(items: List<ActionItem>) :
        BasePrepopulatedListAdapter<ActionItem, ItemViewHolder>(items) {
        override fun onCreateViewHolder(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): ItemViewHolder {
            val binding = ListItemContextMenuBinding.inflate(inflater, parent, false)
            return ItemViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, item: ActionItem) {
            holder.binding.root.text = holder.itemView.context.getString(item.labelResId)
        }
    }

    data class ActionItem(
        val labelResId: Int,
        val action: JiveAction?,
        val download: DownloadRequestData?
    )
    private class ItemViewHolder(val binding: ListItemContextMenuBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        fun create(item: SlimBrowseItemList.SlimBrowseItem) = ItemActionsMenuSheet().apply {
            arguments = bundleOf("item" to item)
        }
    }
}
