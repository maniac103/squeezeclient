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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSourceFactory
import androidx.paging.cachedIn
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.forceGridLayout
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.itemlist.BasePagedSlimBrowseItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.BaseSlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.itemlist.ItemPagingSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RadioSearchResultViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : BasePagedSlimBrowseItemListViewModel(application) {
    override val playerId = savedStateHandle.get<PlayerId>("playerId")!!
    private val searchTerm = savedStateHandle.get<String>("query")!!

    override val titleFlow get() =
        flowOf(listOf(application.getString(R.string.page_title_radio_search, searchTerm)))
    override val iconFlow get() = flowOf(null)

    override val fetchAction get() = null

    override suspend fun fetchPage(page: PagingParams) =
        application.connectionHelper.getRadioSearchResults(playerId, searchTerm, page)
}