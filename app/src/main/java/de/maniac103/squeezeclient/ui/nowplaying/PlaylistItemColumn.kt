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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.model.ServerConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistItemColumn(
    playerId: PlayerId,
    surfaceColor: Color,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<PlaylistViewModel> {
        parametersOf(playerId)
    }

    PlaylistItemColumn(
        viewModel = viewModel,
        surfaceColor = surfaceColor,
        serverConfig = serverConfig,
        modifier = modifier
    )
}

// FIXME: remove this when removing PlaylistFragment
@Composable
fun PlaylistItemColumn(
    viewModel: PlaylistViewModel,
    surfaceColor: Color,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier
) {
    PlaylistItemColumn(
        itemsFlow = viewModel.itemsFlow,
        uiStateFlow = viewModel.uiStateFlow,
        scrollRequestFlow = viewModel.scrollRequestFlow,
        surfaceColor = surfaceColor,
        serverConfig = serverConfig,
        onPlaylistItemSelected = viewModel::setPlaylistPosition,
        onMovePlaylistItem = viewModel::movePlaylistItem,
        onRemovePlaylistItem = viewModel::removePlaylistItem,
        modifier = modifier
    )
}

@Composable
fun PlaylistItemColumn(
    itemsFlow: Flow<PagingData<Playlist.PlaylistItem>>,
    uiStateFlow: StateFlow<PlaylistViewModel.UiState>,
    scrollRequestFlow: Flow<Int>,
    surfaceColor: Color,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier,
    onPlaylistItemSelected: (position: Int) -> Unit = {},
    onMovePlaylistItem: (from: Int, to: Int) -> Unit = { _, _ -> },
    onRemovePlaylistItem: (position: Int) -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val pagingItems = itemsFlow.collectAsLazyPagingItems()
    val uiState by uiStateFlow.collectAsState()

    var dragStartIndex by remember {
        mutableIntStateOf(-1)
    }
    val items = remember {
        mutableStateListOf<Playlist.PlaylistItem>()
    }
    var selectedItemPosition by remember {
        mutableIntStateOf(uiState.currentPosition)
    }

    LaunchedEffect(pagingItems.itemSnapshotList, uiState) {
        if (uiState.pendingMove == null) {
            items.clear()
            items.addAll(
                pagingItems.itemSnapshotList.items
            )
        }
    }
    LaunchedEffect(uiState.currentPosition) {
        selectedItemPosition = uiState.currentPosition
    }

    LaunchedEffect(Unit) {
        scrollRequestFlow.collect { position ->
            lazyListState.animateScrollToItem(position)
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        items.add(to.index, items.removeAt(from.index))
        if (selectedItemPosition == from.index) {
            selectedItemPosition = to.index
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.padding(bottom = 16.dp)
    ) {
        items(
            items = items,
            key = { item -> item.hashCode() }
        ) { item ->
            ReorderableItem(
                reorderableLazyListState,
                key = item.hashCode()
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                Surface(
                    color = surfaceColor,
                    shadowElevation = elevation
                ) {
                    val itemPosition = items.indexOf(item)
                    val dismissState = rememberSwipeToDismissBoxState()

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        onDismiss = { onRemovePlaylistItem(itemPosition) },
                        backgroundContent = { DeleteBackground(dismissState) },
                        content = {
                            PlaylistRow(
                                item = item,
                                serverConfig = serverConfig,
                                isSelected = itemPosition == selectedItemPosition,
                                modifier = Modifier
                                    .clickable(
                                        onClick = { onPlaylistItemSelected(itemPosition) }
                                    )
                                    .background(surfaceColor),
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { offset ->
                                        dragStartIndex = itemPosition
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureEnd
                                        )

                                        dragStartIndex
                                            .takeIf { it >= 0 }
                                            ?.let { onMovePlaylistItem(it, items.indexOf(item)) }
                                        dragStartIndex = -1
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteBackground(
    dismissState: SwipeToDismissBoxState
) {
    val direction = dismissState.dismissDirection

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            SwipeToDismissBoxValue.Settled -> Alignment.Center
        }
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_delete_24dp),
            contentDescription = null, // FIXME
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
fun PlaylistItemColumnPreview() {
    val itemsFlow = (0..4)
        .map { index ->
            Playlist.PlaylistItem(
                title = "Song title $index",
                artist = "Artist $index",
                album = "Album $index",
                actions = JiveActions.EMPTY
            )
        }
        .let { PagingData.from(it) }
        .let { MutableStateFlow(it) }

    val uiStateFlow = MutableStateFlow(PlaylistViewModel.UiState(3, null))
    val scrollRequestFlow: Flow<Int> = flow {}

    PlaylistItemColumn(
        itemsFlow = itemsFlow,
        uiStateFlow = uiStateFlow,
        scrollRequestFlow = scrollRequestFlow,
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        serverConfig = null
    )
}