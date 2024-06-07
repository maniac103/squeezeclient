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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.R
import kotlin.math.abs

class PlaylistItemDragCallback(
    context: Context,
    private val adapter: PlaylistItemAdapter,
    private val doMoveCallback: (PlaylistItemAdapter.SwapInfo) -> Unit,
    private val doRemoveCallback: (Int) -> Unit
) : ItemTouchHelper.Callback() {
    private val deleteIcon = requireNotNull(
        AppCompatResources.getDrawable(context, R.drawable.ic_delete_24dp)
    )
    private val deleteBackgroundPaint = Paint().apply {
        color = context.getColor(R.color.playlist_swipe_delete_background)
    }
    private val tmpRect = RectF()
    private var pendingSwapInfo: PlaylistItemAdapter.SwapInfo? = null
    override fun isLongPressDragEnabled(): Boolean = true
    override fun isItemViewSwipeEnabled(): Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        pendingSwapInfo = adapter.onItemMove(
            viewHolder.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        doRemoveCallback(viewHolder.bindingAdapterPosition)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            viewHolder?.itemView?.isPressed = true
        }

        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        pendingSwapInfo?.let {
            adapter.onItemFinishedMove(it)
            doMoveCallback.invoke(it)
        }
        viewHolder.itemView.isPressed = false
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (dX != 0F && isCurrentlyActive) {
            val itemView = viewHolder.itemView
            val left = if (dX < 0) itemView.width + dX else 0F
            val right = if (dX < 0) itemView.width.toFloat() else dX
            tmpRect.set(left, itemView.top.toFloat(), right, itemView.bottom.toFloat())
            c.drawRect(tmpRect, deleteBackgroundPaint)

            if (abs(dX) > deleteIcon.intrinsicWidth) {
                val iconTop = (tmpRect.centerY() - deleteIcon.intrinsicHeight / 2).toInt()
                val iconLeft = (tmpRect.centerX() - deleteIcon.intrinsicWidth / 2).toInt()
                deleteIcon.setBounds(
                    iconLeft,
                    iconTop,
                    iconLeft + deleteIcon.intrinsicWidth,
                    iconTop + deleteIcon.intrinsicHeight
                )
                deleteIcon.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
