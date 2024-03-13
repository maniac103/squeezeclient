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

package de.maniac103.squeezeclient.ui.itemlist

import android.os.Bundle
import androidx.core.os.bundleOf
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.getParcelableList
import de.maniac103.squeezeclient.model.ListResponse
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.common.BaseSlimBrowseItemListFragment

class SlimBrowseSubItemListFragment : BaseSlimBrowseItemListFragment() {
    interface DataRefreshProvider {
        suspend fun provideRefreshedSubItemList(item: SlimBrowseItemList.SlimBrowseItem):
            List<SlimBrowseItemList.SlimBrowseItem>
    }

    private lateinit var items: List<SlimBrowseItemList.SlimBrowseItem>
    private val parentItem get() =
        requireArguments().getParcelable("parent", SlimBrowseItemList.SlimBrowseItem::class)
    override val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    override val title: String get() = parentItem.title
    override val showIcons = false
    override val useGrid = false
    override val fastScrollEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = requireArguments().getParcelableList("items", SlimBrowseItemList.SlimBrowseItem::class)
    }

    override suspend fun refresh() {
        (activity as? DataRefreshProvider)?.provideRefreshedSubItemList(parentItem)?.let {
            items = it
        }
        super.refresh()
    }

    override suspend fun onLoadPage(
        page: PagingParams
    ): ListResponse<SlimBrowseItemList.SlimBrowseItem> {
        return ItemListWrapper(items)
    }


    data class ItemListWrapper(override val items: List<SlimBrowseItemList.SlimBrowseItem>) :
        ListResponse<SlimBrowseItemList.SlimBrowseItem> {
        override val offset = 0
        override val totalCount = items.size
    }

    companion object {
        fun create(
            playerId: PlayerId,
            parentItem: SlimBrowseItemList.SlimBrowseItem,
            items: List<SlimBrowseItemList.SlimBrowseItem>
        ) = SlimBrowseSubItemListFragment().apply {
            arguments = bundleOf(
                "playerId" to playerId,
                "parent" to parentItem,
                "items" to ArrayList(items)
            )
        }
    }
}
