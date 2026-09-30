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

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.getParcelableOrNull
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.extfuncs.viewModelWithParams
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.ParcelableArtworkItem
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.model.WindowStyle
import de.maniac103.squeezeclient.ui.RefreshableMainContentChild

class SlimBrowseItemListFragment : BaseSlimBrowseItemListFragment(), RefreshableMainContentChild {
    private val viewModel by viewModelWithParams {
        val args = requireArguments()
        val titles = listOfNotNull(
            args.getString("parentTitle"), args.getString("title")
        )

        SlimBrowseItemListViewModel(
            requireActivity().application,
            args.getParcelable("playerId", PlayerId::class),
            titles,
            args.getParcelableOrNull("icon", ParcelableArtworkItem::class),
            args.getParcelable("fetchAction", JiveAction::class),
            args.getBoolean("showIcons"),
            args.getBoolean("canUseGrid")
        )
    }

    override val baseViewModel get() = viewModel

    override fun refresh() {
        viewModel.refresh()
    }

    @Composable
    override fun createListContent(
        itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit,
        contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit
    ) {
        SlimBrowseItemList(
            viewModel,
            prefs.serverConfig,
            itemSelectionListener = itemSelectionListener,
            contextMenuClickListener = contextMenuClickListener
        )
    }

    companion object {
        fun create(
            playerId: PlayerId,
            title: String,
            parentTitle: String?,
            icon: ParcelableArtworkItem?,
            fetchAction: JiveAction,
            windowStyle: WindowStyle?
        ) = SlimBrowseItemListFragment().apply {
            val showIcons = windowStyle != null && windowStyle != WindowStyle.TextOnlyList
            val canUseGrid =
                windowStyle == WindowStyle.IconList || windowStyle == WindowStyle.HomeMenu
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
                putString("title", title)
                putString("parentTitle", parentTitle)
                putParcelable("icon", icon)
                putParcelable("fetchAction", fetchAction)
                putBoolean("canUseGrid", canUseGrid)
                putBoolean("showIcons", showIcons)
            }
        }
    }
}

@Composable
fun SlimBrowseItemList(
    viewModel: SlimBrowseItemListViewModel = viewModel(),
    serverConfig: ServerConfiguration?,
    itemSelectionListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val entries = viewModel.itemsFlow.collectAsLazyPagingItems()
    val busyItem by viewModel.busyItemFlow.collectAsState()

    SlimBrowsePagedItemListOrGrid(
        entries,
        busyItem,
        serverConfig,
        viewModel.useGrid,
        viewModel.showIcons,
        itemSelectionListener,
        contextMenuClickListener
    )
}