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

import androidx.core.os.bundleOf
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.ListResponse
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.common.BaseSlimBrowseItemListFragment

class SlimBrowseSubItemListFragment : BaseSlimBrowseItemListFragment() {
    override val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    override val title: String get() = requireArguments().getString("title")!!
    private val parentFetchAction get() =
        requireArguments().getParcelable("fetchAction", JiveAction::class)
    private val parentItemPosition get() = requireArguments().getInt("listPosition")
    override val showIcons = false
    override val useGrid = false
    override val fastScrollEnabled = false

    override suspend fun onLoadPage(
        page: PagingParams // ignored, because subitems aren't paged
    ): ListResponse<SlimBrowseItemList.SlimBrowseItem> {
        val actualPage = PagingParams(parentItemPosition, 1)
        val parentItemList = connectionHelper
            .fetchItemsForAction(playerId, parentFetchAction, actualPage)
        val parentItem = if (parentItemList.offset == parentItemPosition) {
            parentItemList.items[0]
        } else {
            // server does not support paging for this action
            parentItemList.items[parentItemPosition]
        }
        return ItemListWrapper(requireNotNull(parentItem.subItems))
    }


    data class ItemListWrapper(override val items: List<SlimBrowseItemList.SlimBrowseItem>) :
        ListResponse<SlimBrowseItemList.SlimBrowseItem> {
        override val offset = 0
        override val totalCount = items.size
    }

    companion object {
        fun create(
            playerId: PlayerId,
            title: String,
            fetchAction: JiveAction,
            listPosition: Int
        ) = SlimBrowseSubItemListFragment().apply {
            arguments = bundleOf(
                "playerId" to playerId,
                "title" to title,
                "fetchAction" to fetchAction,
                "listPosition" to listPosition
            )
        }
    }
}
