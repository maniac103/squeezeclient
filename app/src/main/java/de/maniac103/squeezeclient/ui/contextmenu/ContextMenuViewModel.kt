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

package de.maniac103.squeezeclient.ui.contextmenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ContextMenuViewModel(
    private val connectionHelper: ConnectionHelper,
    private val playerId: PlayerId,
    initialItems: List<SlimBrowseItemList.SlimBrowseItem>,
    val parentItem: SlimBrowseItemList.SlimBrowseItem
) : ViewModel() {
    private val pageStackInternal =
        MutableStateFlow(listOf(Page(parentItem, initialItems, false)))
    private val busyItemInternal = MutableStateFlow<SlimBrowseItemList.SlimBrowseItem?>(null)

    val pageStackFlow = pageStackInternal.asStateFlow()
    val busyItemFlow = busyItemInternal.asStateFlow()

    fun pushPage(item: SlimBrowseItemList.SlimBrowseItem, fetchAction: JiveAction) {
        val job = viewModelScope.launch {
            val newItems = connectionHelper.fetchItemsForAction(
                playerId,
                fetchAction,
                PagingParams.All,
                false
            )
            pageStackInternal.value += Page(item, newItems.items, true)
        }
        setItemBusy(item, job)
    }

    fun popPage() {
        val newStack = pageStackInternal.value
            .takeIf { it.size > 1 }
            ?.toMutableList()
            ?.also { it.removeAt(it.lastIndex) }
            ?: return

        pageStackInternal.value = newStack
    }

    fun setItemBusy(item: SlimBrowseItemList.SlimBrowseItem, job: Job) {
        busyItemInternal.value = item
        job.invokeOnCompletion {
            busyItemInternal.value = null
        }
    }

    @Serializable
    data class Page(
        val parent: SlimBrowseItemList.SlimBrowseItem,
        val items: List<SlimBrowseItemList.SlimBrowseItem>,
        val canGoBack: Boolean
    ) : NavKey
}
