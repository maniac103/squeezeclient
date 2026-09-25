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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn

class SlimBrowseSubItemListViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application), BaseSlimBrowseItemListFragment.ViewModelInterface {
    override val playerId = savedStateHandle.get<PlayerId>("playerId")!!
    override val fetchAction = null

    private val parentFetchAction = savedStateHandle.get<JiveAction>("fetchAction")!!
    private val parentItemPosition = savedStateHandle.get<Int>("listPosition")!!

    override val titleFlow = flowOf(
        listOfNotNull(savedStateHandle.get<String>("title"))
    )
    override val iconFlow = flowOf(null)

    private val busyItemInternal = MutableStateFlow<SlimBrowseItemList.SlimBrowseItem?>(null)
    val busyItemFlow = busyItemInternal.asStateFlow()

    val itemsFlow = flow { emit(loadItems()) }
        .shareIn(viewModelScope, SharingStarted.Lazily)

    override fun setItemBusy(item: SlimBrowseItemList.SlimBrowseItem, job: Job) {
        busyItemInternal.value = item
        job.invokeOnCompletion {
            busyItemInternal.value = null
        }
    }

    private suspend fun loadItems(): List<SlimBrowseItemList.SlimBrowseItem> {
        val page = PagingParams(parentItemPosition, 1)
        val parentItemList = application.connectionHelper.fetchItemsForAction(
            playerId,
            parentFetchAction,
            page,
            false
        )
        val parentItem = if (parentItemList.offset == parentItemPosition) {
            parentItemList.items[0]
        } else {
            // server does not support paging for this action
            parentItemList.items[parentItemPosition]
        }
        return requireNotNull(parentItem.subItems)
    }
}