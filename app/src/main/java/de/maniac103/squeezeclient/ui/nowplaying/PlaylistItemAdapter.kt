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

package de.maniac103.squeezeclient.ui.nowplaying

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.ListItemSlimbrowseBinding
import de.maniac103.squeezeclient.model.Playlist

class PlaylistItemAdapter(diffCallback: DiffUtil.ItemCallback<Playlist.PlaylistItem>) :
    PagingDataAdapter<Playlist.PlaylistItem, PlaylistItemViewHolder>(diffCallback) {
    private data class SwapInfo(val fromPos: Int, val toPos: Int)
    private data class PendingSelection(val position: Int?)

    private var swapInfo: SwapInfo? = null
    var dragStartListener: ((PlaylistItemViewHolder) -> Unit)? = null
    var itemClickListener: ((PlaylistItemViewHolder) -> Unit)? = null
    var selectedItemPosition: Int? = null
        set(value) {
            if (swapInfo == null) {
                field?.let { notifyItemChanged(it) }
                value?.let { notifyItemChanged(it) }
                field = value
            } else {
                pendingSelectedPosition = PendingSelection(value)
            }
        }
    private var pendingSelectedPosition: PendingSelection? = null

    init {
        addOnPagesUpdatedListener {
            swapInfo = null
            pendingSelectedPosition?.let { selectedItemPosition = it.position }
            pendingSelectedPosition = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemSlimbrowseBinding.inflate(inflater, parent, false)
        inflater.inflate(R.layout.list_item_extension_drag_handle, binding.contextContainer)

        return PlaylistItemViewHolder(binding).apply {
            itemView.setOnClickListener { itemClickListener?.invoke(this) }
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragStartListener?.invoke(this)
                }
                false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: PlaylistItemViewHolder, position: Int) {
        holder.itemView.isActivated = position == selectedItemPosition
        getItem(position)?.let { holder.bind(it) }
    }

    fun onItemMove(fromPos: Int, toPos: Int) {
        notifyItemMoved(fromPos, toPos)
    }

    fun onItemFinishedMove(fromPos: Int, toPos: Int) {
        swapInfo = SwapInfo(fromPos, toPos)
    }

    fun onItemRemoved(position: Int) {
        notifyItemRemoved(position)
    }
}
