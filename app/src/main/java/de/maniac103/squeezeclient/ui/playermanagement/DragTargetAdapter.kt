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

package de.maniac103.squeezeclient.ui.playermanagement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.databinding.ListItemPlayerDragTargetBinding

class DragTargetAdapter : RecyclerView.Adapter<DragDropAwareViewHolder>() {
    var active = false
        set(value) {
            if (field && !value) {
                notifyItemRemoved(0)
            } else if (!field && value) {
                notifyItemInserted(0)
            }
            field = value
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DragDropAwareViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemPlayerDragTargetBinding.inflate(inflater, parent, false)
        return DragDropAwareViewHolder(binding.root, binding.content, null)
    }

    override fun onBindViewHolder(holder: DragDropAwareViewHolder, position: Int) {
    }

    override fun getItemCount() = if (active) 1 else 0
}
