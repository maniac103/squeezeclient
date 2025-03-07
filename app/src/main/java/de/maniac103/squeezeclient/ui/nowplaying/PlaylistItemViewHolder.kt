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

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.ListItemSlimbrowseBinding
import de.maniac103.squeezeclient.extfuncs.loadArtworkOrPlaceholder
import de.maniac103.squeezeclient.model.Playlist

class PlaylistItemViewHolder(private val binding: ListItemSlimbrowseBinding) :
    RecyclerView.ViewHolder(binding.root) {
    val dragHandle: View = itemView.findViewById(R.id.drag_handle)

    fun bind(item: Playlist.PlaylistItem) {
        binding.title.text = item.title
        binding.subtext.text = when {
            item.artist.isEmpty() -> item.album
            item.album.isEmpty() -> item.artist
            else -> "${item.artist} · ${item.album}"
        }
        binding.icon.loadArtworkOrPlaceholder(item)
    }
}
