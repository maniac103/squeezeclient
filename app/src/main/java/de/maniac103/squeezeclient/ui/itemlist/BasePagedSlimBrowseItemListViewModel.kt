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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSourceFactory
import androidx.paging.cachedIn
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.forceGridLayout
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BasePagedSlimBrowseItemListViewModel(
    private val application: Application,
) : AndroidViewModel(application), BaseSlimBrowseItemListFragment.ViewModelInterface {
    open val useGrid = application.prefs.forceGridLayout ||
        application.resources.getBoolean(R.bool.force_grid_items_for_lists)

    private val busyItemInternal = MutableStateFlow<SlimBrowseItemList.SlimBrowseItem?>(null)
    val busyItemFlow = busyItemInternal.asStateFlow()

    val itemsFlow = createPager()
        .flow
        .cachedIn(viewModelScope)

    override fun setItemBusy(item: SlimBrowseItemList.SlimBrowseItem, job: Job) {
        busyItemInternal.value = item
        job.invokeOnCompletion {
            busyItemInternal.value = null
        }
    }

    private fun createPager(): Pager<Int, SlimBrowseItemList.SlimBrowseItem> {
        val pagingSourceFactory = PagingSourceFactory {
            ItemPagingSource { page -> fetchPage(page) }
        }

        return Pager(PagingConfig(100), pagingSourceFactory = pagingSourceFactory)
    }

    protected abstract suspend fun fetchPage(page: PagingParams): SlimBrowseItemList
}