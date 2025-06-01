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

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.model.PlayerId
import kotlin.math.abs
import kotlinx.coroutines.Job

class PlayerDragMoveCallback(
    private val dragTargetAdapter: DragTargetAdapter,
    private val syncAction: (PlayerId, PlayerId) -> Job?,
    private val unsyncAction: (PlayerId) -> Job?
) : ItemTouchHelper.Callback() {
    private var draggedItem: PlayerViewHolder? = null
    private var lastDropTarget: DragDropAwareViewHolder? = null

    override fun isLongPressDragEnabled() = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
        if (viewHolder is PlayerViewHolder) {
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        } else {
            0
        }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // no-op
    }

    override fun chooseDropTarget(
        selected: RecyclerView.ViewHolder,
        dropTargets: MutableList<RecyclerView.ViewHolder>,
        curX: Int,
        curY: Int
    ): RecyclerView.ViewHolder? {
        val selectedCenterY = curY + selected.itemView.height / 2
        val target = dropTargets
            .sortedWith { a, b ->
                val aYDelta = abs(selectedCenterY - (a.itemView.top + a.itemView.height))
                val bYDelta = abs(selectedCenterY - (b.itemView.top + b.itemView.height))
                aYDelta.compareTo(bYDelta)
            }
            .firstOrNull {
                val targetCenterY = it.itemView.top + it.itemView.height / 2
                val threshold = it.itemView.height / 4
                it !is PlayerViewHolder.SlavePlayerViewHolder &&
                    selectedCenterY >= targetCenterY - threshold &&
                    selectedCenterY <= targetCenterY + threshold
            } as? DragDropAwareViewHolder

        if (lastDropTarget != target) {
            lastDropTarget?.state = DragDropAwareViewHolder.State.Idle
            target?.state = DragDropAwareViewHolder.State.DropTarget
            lastDropTarget = target
        }
        return target
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                draggedItem = viewHolder as? PlayerViewHolder
                // Drop target is only needed when dragging a slave player, as master players
                // can not be unlinked
                if (viewHolder is PlayerViewHolder.SlavePlayerViewHolder) {
                    dragTargetAdapter.active = true
                }
                draggedItem?.state = DragDropAwareViewHolder.State.Dragged
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                val draggedPlayerId = draggedItem?.playerId
                val target = lastDropTarget
                val targetPlayerId = (target as? PlayerViewHolder)?.playerId

                when {
                    draggedPlayerId != null && targetPlayerId != null ->
                        syncAction(targetPlayerId, draggedPlayerId)
                    draggedPlayerId != null && target != null && target !is PlayerViewHolder ->
                        unsyncAction(draggedPlayerId)
                }
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        lastDropTarget?.state = DragDropAwareViewHolder.State.Idle
        lastDropTarget = null
        dragTargetAdapter.active = false
        draggedItem?.state = DragDropAwareViewHolder.State.Idle
        draggedItem = null
    }
}
