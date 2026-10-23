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
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class ItemActionsViewModel(val item: SlimBrowseItemList.SlimBrowseItem) : ViewModel() {
    private val busyActionFlowInternal = MutableStateFlow<ActionItem?>(null)

    val actionsFlow = flowOf(
        requireNotNull(item.actions).let { actions ->
            listOfNotNull(
                actions.addAction?.let {
                    ActionItem(R.string.action_add, ItemAction.Action(it))
                },
                actions.insertAction?.let {
                    ActionItem(R.string.action_insert, ItemAction.Action(it))
                },
                actions.playAction?.let {
                    ActionItem(R.string.action_play, ItemAction.Action(it))
                },
                actions.downloadData?.let {
                    ActionItem(R.string.action_download, ItemAction.Download(it))
                }
            )
        }
    )
    val busyActionFlow = busyActionFlowInternal.asStateFlow()

    fun setActionBusy(action: ActionItem, job: Job) {
        busyActionFlowInternal.value = action
        job.invokeOnCompletion {
            busyActionFlowInternal.value = null
        }
    }

    data class ActionItem(val labelResId: Int, val action: ItemAction)
}

sealed interface ItemAction {
    data class Action(val action: JiveAction) : ItemAction
    data class Download(val download: DownloadRequestData) : ItemAction
}
