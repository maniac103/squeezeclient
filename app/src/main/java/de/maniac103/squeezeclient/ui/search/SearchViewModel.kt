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

package de.maniac103.squeezeclient.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.extfuncs.backProgressInterpolator
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.LocalLibrarySearchResultCounts
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SearchViewModel(
    private val application: Application,
    private val playerId: PlayerId
) : AndroidViewModel(application) {
    private val localResultsFlow = MutableStateFlow<List<Category>>(createLocalCategories(null))
    private val radioResultsFLow = MutableStateFlow<Category>(createRadioCategory(null))
    private val queryTextFlow = MutableStateFlow<QueryState>(QueryState("", false))
    private val backProgressFlowInternal = MutableStateFlow(0F)

    private var submitJob: Job? = null
    private var searchJob: Job? = null

    val categoriesFlow = combine(localResultsFlow, radioResultsFLow) { local, radio ->
        local + radio
    }
    val queryFlow = queryTextFlow.asStateFlow()
    val backProgressFlow = backProgressFlowInternal.asStateFlow()

    fun setBackProgress(progress: Float) {
        backProgressFlowInternal.value =
            0.3F * application.backProgressInterpolator.getInterpolation(progress)
    }

    fun updateQuery(query: String) {
        localResultsFlow.value = createLocalCategories(null)
        radioResultsFLow.value = createRadioCategory(null)

        submitJob?.cancel()
        searchJob?.cancel()

        queryTextFlow.value = QueryState(query, false)
        if (query.isNotEmpty()) {
            submitJob = viewModelScope.launch {
                delay(1.seconds)
                submitJob = submitQuery(query)
            }
        }
    }

    fun submitQueryImmediately() {
        if (searchJob?.isActive != true) {
            val query = queryTextFlow.value.text
            submitJob?.cancel()
            searchJob?.cancel()
            searchJob = submitQuery(query)
        }
    }

    private fun submitQuery(query: String) = viewModelScope.launch {
        launch {
            val results =
                application.connectionHelper.getLocalLibrarySearchResultCounts(query)
            localResultsFlow.value = createLocalCategories(results)
        }
        launch {
            val results = application.connectionHelper.getRadioSearchResults(
                playerId,
                query,
                PagingParams.CountOnly
            )
            radioResultsFLow.value = createRadioCategory(results.totalCount)
        }
        queryTextFlow.value = QueryState(query, true)
    }

    private fun createLocalCategories(counts: LocalLibrarySearchResultCounts?) = listOf(
        createCategory(
            R.string.search_category_artists,
            LibrarySearchRequest.Mode.Artist(),
            counts?.artists
        ),
        createCategory(
            R.string.search_category_albums,
            LibrarySearchRequest.Mode.Albums(),
            counts?.albums
        ),
        createCategory(
            R.string.search_category_genres,
            LibrarySearchRequest.Mode.Genres(),
            counts?.genres
        ),
        createCategory(
            R.string.search_category_tracks,
            LibrarySearchRequest.Mode.Tracks(),
            counts?.tracks
        ),
    )

    private fun createRadioCategory(count: Int?) =
        createCategory(R.string.search_category_radio, null, count)

    private fun createCategory(
        titleResId: Int,
        type: LibrarySearchRequest.Mode?,
        count: Int?
    ) = Category(
        application.getString(titleResId),
        type,
        count ?: 0,
        count == null
    )

    data class QueryState(
        val text: String,
        val submitted: Boolean
    )
    data class Category(
        val title: String,
        val type: LibrarySearchRequest.Mode?,
        val resultCount: Int,
        val busy: Boolean
    )
}