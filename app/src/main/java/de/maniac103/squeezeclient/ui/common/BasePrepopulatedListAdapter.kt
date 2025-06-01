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

package de.maniac103.squeezeclient.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job

abstract class BasePrepopulatedListAdapter<T, VH : RecyclerView.ViewHolder>(items: List<T>) :
    RecyclerView.Adapter<VH>() {
    fun interface ItemSelectionListener<T> {
        fun onItemSelected(item: T): Job?
    }

    private val internalItems = items.toMutableList()
    var itemSelectionListener: ItemSelectionListener<T>? = null

    protected abstract fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): VH
    protected abstract fun onBindViewHolder(holder: VH, item: T)
    protected open fun onHolderBusyStateChanged(holder: VH, busy: Boolean) {}
    protected open fun getItemViewType(item: T): Int = 0

    @SuppressLint("NotifyDataSetChanged")
    fun replaceItems(items: List<T>) {
        if (items.size != internalItems.size) {
            internalItems.clear()
            internalItems.addAll(items)
            notifyDataSetChanged()
        } else {
            items.forEachIndexed { index, item ->
                if (item != internalItems[index]) {
                    internalItems[index] = item
                    notifyItemChanged(index)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return onCreateViewHolder(inflater, parent, viewType).apply {
            itemView.setOnClickListener {
                bindingAdapterPosition
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { internalItems[it] }
                    ?.let { itemSelectionListener?.onItemSelected(it) }
                    ?.let { job ->
                        onHolderBusyStateChanged(this, true)
                        job.invokeOnCompletion { onHolderBusyStateChanged(this, false) }
                    }
            }
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBindViewHolder(holder, internalItems[position])
    }

    override fun getItemViewType(position: Int) = getItemViewType(internalItems[position])

    override fun getItemCount() = internalItems.size
}
