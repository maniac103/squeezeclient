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

import android.content.res.Configuration
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.databinding.FragmentGenericListBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.ui.common.BasePagingListFragment
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalTime::class)
class PlaylistFragment :
    BasePagingListFragment<Playlist.PlaylistItem, PlaylistItemViewHolder>() {
    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    override val fastScrollEnabled = true
    override val useGrid = false

    private lateinit var adapter: PlaylistItemAdapter
    private var lastKnownPlaylistTimestamp: Instant? = null

    fun scrollToCurrentPlaylistPosition() {
        val position = adapter.selectedItemPosition ?: return
        binding.recycler.scrollToPosition(position)
    }

    override fun onCreateAdapter(
        diffCallback: DiffUtil.ItemCallback<Playlist.PlaylistItem>
    ): PagingDataAdapter<Playlist.PlaylistItem, PlaylistItemViewHolder> {
        val itemTouchCallback = PlaylistItemDragCallback(requireContext(), { from, to ->
            // Item was dragged to another position
            adapter.onItemMove(from, to)
        }, { initial, drop ->
            // Item was dropped after being dragged
            lifecycleScope.launch {
                adapter.onItemFinishedMove(initial, drop)
                connectionHelper.movePlaylistItem(playerId, initial, drop)
            }
        }, { position ->
            // Item was removed via swipe
            lifecycleScope.launch {
                connectionHelper.removePlaylistItem(playerId, position)
                adapter.onItemRemoved(position)
            }
        })

        val itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper.attachToRecyclerView(binding.recycler)

        adapter = PlaylistItemAdapter(diffCallback).apply {
            dragStartListener = { holder -> itemTouchHelper.startDrag(holder) }
            itemClickListener = { holder ->
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    lifecycleScope.launch {
                        connectionHelper.advanceToPlaylistPosition(playerId, position)
                    }
                }
            }
        }
        return adapter
    }

    override suspend fun onLoadPage(page: PagingParams) =
        connectionHelper.fetchPlaylist(playerId, page)

    override fun areItemsTheSame(lhs: Playlist.PlaylistItem, rhs: Playlist.PlaylistItem) =
        lhs == rhs

    override fun areItemContentsTheSame(lhs: Playlist.PlaylistItem, rhs: Playlist.PlaylistItem) =
        lhs == rhs

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onBindingCreated(binding: FragmentGenericListBinding) {
        super.onBindingCreated(binding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recycler) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // in landscape we're placed at the end edge -> only apply respective inset
                val startIsLeft = v.layoutDirection == View.LAYOUT_DIRECTION_LTR
                val leftPadding = if (startIsLeft) 0 else insets.left
                val rightPadding = if (startIsLeft) insets.right else 0
                v.updatePadding(left = leftPadding, right = rightPadding, bottom = insets.bottom)
            } else {
                // in portrait, we fill the whole width -> apply both inset sides
                v.updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
            }
            windowInsets
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.playStatus }
                    .collect { status ->
                        val lastPlaylistTimestamp = lastKnownPlaylistTimestamp
                        val newPlaylistTimestamp = status.playlist.lastChange
                        if (
                            lastPlaylistTimestamp != null &&
                            lastPlaylistTimestamp != newPlaylistTimestamp
                        ) {
                            refresh()
                        }
                        lastKnownPlaylistTimestamp = newPlaylistTimestamp
                        adapter.selectedItemPosition = status.playlist.currentPosition - 1
                    }
            }
        }
    }

    companion object {
        fun create(playerId: PlayerId) = PlaylistFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
