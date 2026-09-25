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
import androidx.lifecycle.SavedStateHandle
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.coroutines.flow.flowOf

class SlimBrowseItemListViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : BasePagedSlimBrowseItemListViewModel(application) {
    override val playerId = savedStateHandle.get<PlayerId>("playerId")!!

    override val titleFlow = flowOf(
        listOfNotNull(
            savedStateHandle.get<String>("parentTitle"),
            savedStateHandle.get<String>("title")
        )
    )
    override val iconFlow = flowOf(savedStateHandle.get<ArtworkItem>("icon"))

    val showIcons = savedStateHandle.get<Boolean>("showIcons") == true
    override val useGrid = when {
        savedStateHandle.get<Boolean>("canUseGrid") == false -> false
        else -> super.useGrid
    }

    override val fetchAction = savedStateHandle.get<JiveAction>("fetchAction")!!

    override suspend fun fetchPage(page: PagingParams) =
        application.connectionHelper.fetchItemsForAction(playerId, fetchAction, page, true)
}