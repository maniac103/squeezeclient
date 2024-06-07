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

package de.maniac103.squeezeclient.ui.itemlist

import androidx.core.os.bundleOf
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.ListResponse
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.model.WindowStyle
import de.maniac103.squeezeclient.ui.common.BaseSlimBrowseItemListFragment
import kotlinx.coroutines.Job

class SlimBrowseItemListFragment : BaseSlimBrowseItemListFragment() {
    override val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    override val title get() = requireArguments().getString("title")!!
    override val showIcons get() = requireArguments().getBoolean("showIcons")
    override val useGrid get() = super.useGrid && requireArguments().getBoolean("canUseGrid")
    override val fetchAction get() =
        requireArguments().getParcelable("fetchAction", JiveAction::class)
    override val fastScrollEnabled = true

    override suspend fun onLoadPage(
        page: PagingParams
    ): ListResponse<SlimBrowseItemList.SlimBrowseItem> {
        return connectionHelper.fetchItemsForAction(playerId, fetchAction, page)
    }

    companion object {
        fun create(
            playerId: PlayerId,
            title: String,
            fetchAction: JiveAction,
            windowStyle: WindowStyle?
        ) = SlimBrowseItemListFragment().apply {
            val showIcons = windowStyle != WindowStyle.TextOnlyList
            val canUseGrid =
                windowStyle == WindowStyle.IconList || windowStyle == WindowStyle.HomeMenu
            arguments = bundleOf(
                "playerId" to playerId,
                "title" to title,
                "fetchAction" to fetchAction,
                "canUseGrid" to canUseGrid,
                "showIcons" to showIcons
            )
        }
    }
}
