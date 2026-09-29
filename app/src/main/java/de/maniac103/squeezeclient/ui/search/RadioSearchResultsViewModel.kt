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

package de.maniac103.squeezeclient.ui.search

import android.app.Application
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.itemlist.BasePagedSlimBrowseItemListViewModel
import kotlinx.coroutines.flow.flowOf

class RadioSearchResultViewModel(
    private val application: Application,
    override val playerId: PlayerId,
    private val searchTerm: String
) : BasePagedSlimBrowseItemListViewModel(application) {
    override val titleFlow get() =
        flowOf(listOf(application.getString(R.string.page_title_radio_search, searchTerm)))
    override val iconFlow get() = flowOf(null)

    override val fetchAction get() = null

    override suspend fun fetchPage(page: PagingParams) =
        application.connectionHelper.getRadioSearchResults(playerId, searchTerm, page)
}