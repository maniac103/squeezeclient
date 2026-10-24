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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

abstract class BasePagedSlimBrowseItemListViewModel : ViewModel() {
    private val busyItemInternal = MutableStateFlow<SlimBrowseItemList.SlimBrowseItem?>(null)
    val busyItemFlow = busyItemInternal.asStateFlow()

    protected val pagingSourceFactory = InvalidatingPagingSourceFactory {
        ItemPagingSource { page -> fetchPage(page) }
    }

    val itemsFlow = Pager(
        PagingConfig(pageSize = 200, initialLoadSize = 200),
        pagingSourceFactory = pagingSourceFactory
    )
        .flow
        .map { data -> data.map { it.toUiItem() } }
        .cachedIn(viewModelScope)

    internal fun handleItemSelection(
        item: SlimBrowseItemList.SlimBrowseItem,
        handler: (SlimBrowseItemList.SlimBrowseItem) -> Job?
    ) {
        handler(item)?.apply {
            busyItemInternal.value = item
            invokeOnCompletion {
                busyItemInternal.value = null
            }
        }
    }

    fun refresh() {
        pagingSourceFactory.invalidate()
    }

    protected abstract suspend fun fetchPage(page: PagingParams): SlimBrowseItemList
}
