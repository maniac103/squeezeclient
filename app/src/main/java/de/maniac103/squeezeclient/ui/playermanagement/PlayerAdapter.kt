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

package de.maniac103.squeezeclient.ui.playermanagement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import de.maniac103.squeezeclient.databinding.ListItemMasterPlayerBinding
import de.maniac103.squeezeclient.databinding.ListItemSlavePlayerBinding

class PlayerAdapter(
    private val callback: PlayerViewHolder.Callback
) : ListAdapter<PlayerData, PlayerViewHolder>(PlayerDataDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_MASTER) {
            val binding = ListItemMasterPlayerBinding.inflate(inflater, parent, false)
            PlayerViewHolder.MasterPlayerViewHolder(binding, callback)
        } else {
            val binding = ListItemSlavePlayerBinding.inflate(inflater, parent, false)
            PlayerViewHolder.SlavePlayerViewHolder(binding, callback)
        }
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).master == null) VIEW_TYPE_MASTER else VIEW_TYPE_SLAVE

    companion object {
        private const val VIEW_TYPE_MASTER = 0
        private const val VIEW_TYPE_SLAVE = 1
    }

    class PlayerDataDiffCallback : DiffUtil.ItemCallback<PlayerData>() {
        override fun areItemsTheSame(oldItem: PlayerData, newItem: PlayerData) =
            oldItem.playerId == newItem.playerId

        override fun areContentsTheSame(oldItem: PlayerData, newItem: PlayerData) =
            oldItem == newItem
    }
}
