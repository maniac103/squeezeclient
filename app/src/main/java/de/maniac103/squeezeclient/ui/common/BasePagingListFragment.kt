/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.PagingSource
import androidx.paging.PagingSourceFactory
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.FragmentGenericListBinding
import de.maniac103.squeezeclient.model.ListResponse
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.ui.widget.AutoFitGridLayoutManager
import kotlin.math.max
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder

abstract class BasePagingListFragment<T : Any, VH : RecyclerView.ViewHolder> :
    Fragment(),
    ScrollingListFragment {
    protected lateinit var binding: FragmentGenericListBinding
    private lateinit var adapter: PagingDataAdapter<T, VH>

    override val scrollingTargetView get() = binding.recycler

    protected abstract val fastScrollEnabled: Boolean
    protected open val useGrid get() = resources.getBoolean(R.bool.use_grid_items_for_lists)

    protected abstract fun onCreateAdapter(
        diffCallback: DiffUtil.ItemCallback<T>
    ): PagingDataAdapter<T, VH>
    protected abstract suspend fun onLoadPage(page: PagingParams): ListResponse<T>
    protected abstract fun areItemsTheSame(lhs: T, rhs: T): Boolean
    protected abstract fun areItemContentsTheSame(lhs: T, rhs: T): Boolean
    protected open fun onDataLoaded(data: PagingData<T>) {}

    open suspend fun refresh() {
        adapter.refresh()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentGenericListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val diffCallback = object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
                return this@BasePagingListFragment.areItemsTheSame(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
                return this@BasePagingListFragment.areItemContentsTheSame(oldItem, newItem)
            }
        }

        binding.recycler.layoutManager = if (useGrid) {
            val columnWidth = resources.getDimensionPixelSize(R.dimen.item_grid_column_width)
            AutoFitGridLayoutManager(requireContext(), columnWidth)
        } else {
            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        }

        adapter = onCreateAdapter(diffCallback)
        binding.recycler.adapter = adapter

        if (fastScrollEnabled) {
            FastScrollerBuilder(binding.recycler)
                .useMd2Style()
                .build()
        }

        binding.progress.isVisible = true

        val pagingSourceFactory = PagingSourceFactory {
            ItemSource { page ->
                if (!isAdded) throw IllegalStateException("Fragment not attached")
                onLoadPage(page)
            }
        }
        val pager = Pager(PagingConfig(100), pagingSourceFactory = pagingSourceFactory)
        val flow = pager.flow.cachedIn(lifecycleScope)
        lifecycleScope.launch {
            flow.collectLatest {
                onDataLoaded(it)
                adapter.submitData(it)
            }
        }
        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                binding.progress.isVisible = loadStates.refresh is LoadState.Loading
            }
        }
    }

    private class ItemSource<T : Any>(
        private val producer: suspend (PagingParams) -> ListResponse<T>
    ) : PagingSource<Int, T>() {
        override fun getRefreshKey(state: PagingState<Int, T>): Int? {
            return state.anchorPosition?.let { anchorPosition ->
                val anchorPage = state.closestPageToPosition(anchorPosition)
                anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
            }
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
}
