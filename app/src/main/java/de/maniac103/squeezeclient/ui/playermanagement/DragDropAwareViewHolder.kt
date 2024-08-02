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

import android.view.View
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.extfuncs.animateScale
import kotlin.time.Duration.Companion.milliseconds

open class DragDropAwareViewHolder(
    itemView: View,
    private val contentView: View,
    private val dragHandle: View?
) : RecyclerView.ViewHolder(itemView) {
    enum class State {
        Idle,
        Dragged,
        DropTarget
    }

    var state = State.Idle
        set(value) {
            updateForState(value)
            field = value
        }

    private fun updateForState(state: State) {
        itemView.animateScale(
            when (state) {
                State.Dragged -> 0.95F
                else -> 1.0F
            },
            ANIMATION_DURATION
        )
        contentView.animateScale(
            when (state) {
                State.DropTarget -> 0.9F
                else -> 1.0F
            },
            ANIMATION_DURATION
        )

        itemView.isSelected = state == State.DropTarget
        itemView.isActivated = state == State.Dragged
        dragHandle?.isInvisible = state == State.Dragged
        contentView.alpha = if (state == State.DropTarget) 0.5F else 1.0F
    }

    companion object {
        val ANIMATION_DURATION = 250.milliseconds
    }
}
