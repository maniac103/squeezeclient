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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job

abstract class BasePrepopulatedListAdapter<T, VH : RecyclerView.ViewHolder>(items: List<T>) :
    RecyclerView.Adapter<VH>() {
    fun interface ItemSelectionListener<T> {
        fun onItemSelected(item: T): Job?
    }

    private val internalItems: MutableList<T>
    var itemSelectionListener: ItemSelectionListener<T>? = null

    protected abstract fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): VH
    protected abstract fun onBindViewHolder(holder: VH, item: T)
    protected open fun onHolderBusyStateChanged(holder: VH, busy: Boolean) {}
    protected open fun getItemViewType(item: T): Int = 0

    init {
        internalItems = items.toMutableList()
    }

    fun replaceItem(position: Int, item: T) {
        if (position >= 0 && position < internalItems.size && item != internalItems[position]) {
            internalItems[position] = item
            notifyItemChanged(position)
        }
    }
    fun replaceItems(items: List<T>) {
        if (items.size != internalItems.size) {
            internalItems.clear()
            internalItems.addAll(items)
            notifyDataSetChanged()
        } else {
            (0 until items.size).forEach { replaceItem(it, items[it]) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val holder = onCreateViewHolder(inflater, parent, viewType)
        holder.itemView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                itemSelectionListener?.onItemSelected(internalItems[position])?.let { job ->
                    onHolderBusyStateChanged(holder, true)
                    job.invokeOnCompletion { onHolderBusyStateChanged(holder, false) }
                }
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBindViewHolder(holder, internalItems[position])
    }

    override fun getItemViewType(position: Int): Int {
        return getItemViewType(internalItems[position])
    }

    override fun getItemCount(): Int {
        return internalItems.size
    }
}
