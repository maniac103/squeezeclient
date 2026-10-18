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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import kotlin.math.roundToInt

@Composable
internal fun PlayerList(
    players: List<PlayerData>,
    slavesByMaster: Map<PlayerId, List<PlayerId>>,
    onChangeVolume: (PlayerId, Int) -> Unit,
    onTogglePower: (PlayerId) -> Unit,
    onChangePlaybackState: (PlayerId, PlayerStatus.PlayState) -> Unit,
    onSync: (PlayerId, PlayerId) -> Unit,
    onUnsync: (PlayerId) -> Unit,
    contentPadding: PaddingValues
) {
    val listState = rememberLazyListState()
    val dragState = remember { PlayerDragState() }
    val currentPlayers = rememberUpdatedState(players)
    val maxScrollSpeed = with(LocalDensity.current) { 900.dp.toPx() } // px per second

    val isDragging = dragState.draggedPlayerId != null
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val velocity = dragState.autoScrollVelocity(listState, maxScrollSpeed)
            if (velocity != 0F) {
                listState.scrollBy(velocity * dt)
            }
            dragState.updateHover(currentPlayers.value, listState)
        }
    }

    // Abort if the dragged player vanishes (e.g. disconnect)
    val draggedPlayer = dragState.draggedPlayerId?.let { id ->
        players.firstOrNull { it.playerId == id }
    }
    val draggedGone = isDragging && draggedPlayer == null
    LaunchedEffect(draggedGone) {
        if (draggedGone) {
            dragState.cancel()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .playerDragGestures(
                    dragState = dragState,
                    listState = listState,
                    players = currentPlayers,
                    onSync = onSync,
                    onUnsync = onUnsync
                )
        ) {
            items(
                items = players,
                key = { it.playerId.listKey }
            ) { player ->
                val isDragged = dragState.draggedPlayerId == player.playerId
                val isDropTarget = (dragState.hovered as? DropTarget.Player)?.id == player.playerId
                val slaveListForPlayerMaster = player.master?.let { slavesByMaster.getValue(it) }
                val positionInSlaveList = slaveListForPlayerMaster?.indexOf(player.playerId)

                val position = when {
                    positionInSlaveList != null &&
                        positionInSlaveList == slaveListForPlayerMaster.size - 1 -> RowPosition.Last

                    positionInSlaveList != null -> RowPosition.Middle

                    slavesByMaster[player.playerId] == null -> RowPosition.Single

                    else -> RowPosition.First
                }

                PlayerRow(
                    player = player,
                    state = when (isDropTarget) {
                        true -> RowState.DropTarget
                        false -> RowState.Idle
                    },
                    position = position,
                    modifier = Modifier
                        .animateItem()
                        .alpha(if (isDragged) 0f else 1f),
                    dragHandleModifier = with(dragState) {
                        Modifier.dragHandleModifier(player.playerId.listKey)
                    },
                    onChangePlaybackState = onChangePlaybackState,
                    onTogglePower = onTogglePower,
                    onChangeVolume = onChangeVolume
                )
            }

            if (isDragging && dragState.draggedIsSlave) {
                item(key = UNSYNC_TARGET_LIST_KEY) {
                    DragTargetRow(
                        state = when (dragState.hovered) {
                            DropTarget.Unsync -> RowState.DropTarget
                            else -> RowState.Idle
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        if (draggedPlayer != null) {
            PlayerRow(
                player = draggedPlayer,
                state = RowState.Dragged,
                position = RowPosition.Single,
                onChangePlaybackState = onChangePlaybackState,
                onTogglePower = onTogglePower,
                onChangeVolume = onChangeVolume,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragState.previewTop.roundToInt()) }
            )

            if (dragState.hovered is DropTarget.Player) {
                val height = dragState.determineItemHeight(draggedPlayer.playerId.listKey)
                AnimatedVisibility(visible = height != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height ?: 0.dp)
                            .offset { IntOffset(0, dragState.previewTop.roundToInt()) }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_link_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.playerDragGestures(
    dragState: PlayerDragState,
    listState: LazyListState,
    players: State<List<PlayerData>>,
    onSync: (PlayerId, PlayerId) -> Unit,
    onUnsync: (PlayerId) -> Unit
) = this
    .onGloballyPositioned { dragState.listCoordinates = it }
    .pointerInput(dragState, listState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val key = dragState.findKeyAtPosition(down.position)
                ?: return@awaitEachGesture
            val player = players.value
                .firstOrNull { key == it.playerId.listKey }
                ?: return@awaitEachGesture
            val item = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key }
                ?: return@awaitEachGesture

            down.consume()
            dragState.start(
                player = player,
                top = item.offset.toFloat() + listState.layoutInfo.beforeContentPadding,
                height = item.size.toFloat()
            )

            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .firstOrNull { it.id == down.id }
                if (change == null) {
                    dragState.cancel()
                    break
                }
                if (!change.pressed) {
                    dragState.end()?.let { (draggedPlayerId, dropTarget) ->
                        when (dropTarget) {
                            is DropTarget.Player -> onSync(dropTarget.id, draggedPlayerId)
                            is DropTarget.Unsync -> onUnsync(draggedPlayerId)
                        }
                    }
                    break
                }
                dragState.dragBy(
                    delta = change.position.y - change.previousPosition.y,
                    players = players.value,
                    listState = listState
                )
                change.consume()
            }
        }
    }
