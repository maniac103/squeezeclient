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

package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp

sealed interface SlimBrowseItemListMode {
    val scrollIndicatorState: ScrollIndicatorState?

    data class List(val listState: LazyListState, val showIcons: Boolean) : SlimBrowseItemListMode {
        override val scrollIndicatorState get() = listState.scrollIndicatorState
    }

    data class Grid(val gridState: LazyGridState, val cellMinSize: Dp) : SlimBrowseItemListMode {
        override val scrollIndicatorState get() = gridState.scrollIndicatorState
    }
}

@Composable
fun rememberSlimBrowseItemListMode(
    useGrid: Boolean,
    showIcons: Boolean,
    gridCellMinSize: Dp,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
): SlimBrowseItemListMode {
    val lastUsedGrid = remember { mutableStateOf(useGrid) }

    SideEffect {
        if (lastUsedGrid.value != useGrid) {
            if (useGrid) {
                gridState.requestScrollToItem(listState.firstVisibleItemIndex)
            } else {
                listState.requestScrollToItem(gridState.firstVisibleItemIndex)
            }
            lastUsedGrid.value = useGrid
        }
    }

    return remember(useGrid, showIcons, listState, gridState) {
        if (useGrid) {
            SlimBrowseItemListMode.Grid(gridState, gridCellMinSize)
        } else {
            SlimBrowseItemListMode.List(listState, showIcons)
        }
    }
}
