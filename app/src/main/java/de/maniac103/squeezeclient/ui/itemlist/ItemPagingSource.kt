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

import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.maniac103.squeezeclient.model.ListResponse
import de.maniac103.squeezeclient.model.PagingParams
import kotlin.math.max

class ItemPagingSource<T : Any>(
    private val producer: suspend (PagingParams) -> ListResponse<T>
) : PagingSource<Int, T>() {
    override fun getRefreshKey(state: PagingState<Int, T>) =
        state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = try {
        val pageNumber = params.key ?: 0
        val result = producer(PagingParams(pageNumber * params.loadSize, params.loadSize))
        val nextKey = if (result.serverHasMoreData) pageNumber + 1 else null
        val remainder = result.totalCount - result.offset - result.items.size
        LoadResult.Page(
            data = result.items,
            prevKey = null,
            nextKey = nextKey,
            itemsBefore = result.offset,
            itemsAfter = max(remainder, 0)
        )
    } catch (e: IllegalStateException) {
        LoadResult.Error(e)
    }
}
