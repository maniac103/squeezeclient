/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.model.PlayerId
import kotlin.math.abs
import kotlin.math.sign

sealed interface DropTarget {
    data class Player(val id: PlayerId) : DropTarget
    data object Unsync : DropTarget
}

internal val PlayerId.listKey get() = id
internal const val UNSYNC_TARGET_LIST_KEY = "unsync-target"

@Stable
class PlayerDragState {
    var draggedPlayerId by mutableStateOf<PlayerId?>(null)
        private set
    var draggedIsSlave by mutableStateOf(false)
        private set
    var hovered by mutableStateOf<DropTarget?>(null)
        private set

    private var dragStartTop by mutableFloatStateOf(0F)
    private var dragOffsetY by mutableFloatStateOf(0F)
    private var draggedItemHeight = 0F

    val previewTop get() = dragStartTop + dragOffsetY

    var listCoordinates: LayoutCoordinates? = null
    private val handles = mutableMapOf<String, LayoutCoordinates>()

    @Composable
    fun Modifier.dragHandleModifier(key: String): Modifier {
        DisposableEffect(key) {
            onDispose { handles.remove(key) }
        }
        return onGloballyPositioned { handles[key] = it }
    }

    fun findKeyAtPosition(position: Offset): String? {
        val list = listCoordinates
            ?.takeIf { it.isAttached }
            ?: return null
        return handles.entries
            .firstOrNull { (_, c) ->
                c.isAttached && list.localBoundingBoxOf(c).contains(position)
            }
            ?.key
    }

    fun determineItemHeight(key: String) = handles[key]?.size?.height?.dp

    // --- drag lifecycle ---
    fun start(player: PlayerData, top: Float, height: Float) {
        draggedPlayerId = player.playerId
        draggedIsSlave = player.master != null
        dragStartTop = top
        dragOffsetY = 0F
        draggedItemHeight = height
        hovered = null
    }

    fun dragBy(delta: Float, players: List<PlayerData>, listState: LazyListState) {
        dragOffsetY += delta
        updateHover(players, listState)
    }

    fun updateHover(players: List<PlayerData>, listState: LazyListState) {
        val id = draggedPlayerId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo.associateBy { it.key }
        val draggedItemCenterY = previewTop + draggedItemHeight / 2f

        fun isHovered(key: String): Boolean {
            val info = visible[key] ?: return false
            val itemCenterY =
                info.offset + listState.layoutInfo.beforeContentPadding + info.size / 2F
            return abs(draggedItemCenterY - itemCenterY) <= info.size / 4F
        }

        hovered = players
            .firstOrNull {
                it.master == null && it.playerId != id && isHovered(it.playerId.listKey)
            }
            ?.let { DropTarget.Player(it.playerId) }
            ?: DropTarget.Unsync.takeIf { draggedIsSlave && isHovered(UNSYNC_TARGET_LIST_KEY) }
    }

    fun autoScrollVelocity(listState: LazyListState, maxSpeed: Float): Float {
        if (draggedPlayerId == null) {
            return 0F
        }
        val info = listState.layoutInfo
        val areaTop = info.beforeContentPadding.toFloat()
        val areaBottom = (info.viewportSize.height - info.afterContentPadding).toFloat()
        val bottom = previewTop + draggedItemHeight
        val overshoot = when {
            previewTop < areaTop -> previewTop - areaTop
            bottom > areaBottom -> bottom - areaBottom
            else -> return 0F
        }
        val ratio = (abs(overshoot) / draggedItemHeight).coerceIn(0.15f, 1f)
        return sign(overshoot) * maxSpeed * ratio
    }

    fun end(): Pair<PlayerId, DropTarget>? {
        val id = draggedPlayerId
        val target = hovered
        cancel()
        return if (id == null || target == null) {
            null
        } else {
            id to target
        }
    }

    fun cancel() {
        draggedPlayerId = null
        draggedIsSlave = false
        hovered = null
        dragOffsetY = 0F
    }
}