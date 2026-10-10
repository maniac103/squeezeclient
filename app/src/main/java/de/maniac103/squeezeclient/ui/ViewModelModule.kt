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

package de.maniac103.squeezeclient.ui

import de.maniac103.squeezeclient.SqueezeClientApplication
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetViewModel
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsViewModel
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemListViewModel
import de.maniac103.squeezeclient.ui.maincontent.MainContentViewModel
import de.maniac103.squeezeclient.ui.nowplaying.NowPlayingViewModel
import de.maniac103.squeezeclient.ui.nowplaying.PlaylistViewModel
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsViewModel
import de.maniac103.squeezeclient.ui.search.RadioSearchResultViewModel
import de.maniac103.squeezeclient.ui.search.SearchViewModel
import de.maniac103.squeezeclient.ui.volume.VolumePopupViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    single {
        (androidApplication() as SqueezeClientApplication).connectionHelper
    }

    viewModel { params ->
        NowPlayingViewModel(playerId = params.get<PlayerId>(), connectionHelper = get())
    }
    viewModel { params ->
        PlaylistViewModel(playerId = params.get<PlayerId>(), connectionHelper = get())
    }
    viewModel { params ->
        ContextMenuBottomSheetViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>(),
            initialItems = params.get<List<SlimBrowseItemList.SlimBrowseItem>>(),
            parentItem = params.get<SlimBrowseItemList.SlimBrowseItem>()
        )
    }
    viewModel { params ->
        ItemActionsViewModel(item = params.get<SlimBrowseItemList.SlimBrowseItem>())
    }
    viewModel { params ->
        JiveHomeItemListViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>(),
            nodeId = params.get<String>()
        )
    }
    viewModel { params ->
        SlimBrowseItemListViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>(),
            fetchAction = params.get<JiveAction>()
        )
    }
    viewModel { params ->
        SlimBrowseSubItemListViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>(),
            parentFetchAction = params.get<JiveAction>(),
            parentItemPosition = params.get<Int>()
        )
    }
    viewModel { params ->
        LibrarySearchResultsViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>(),
            searchTerm = params.get<String>(),
            searchType = params.get<LibrarySearchRequest.Mode>()
        )
    }
    viewModel { params ->
        RadioSearchResultViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>(),
            searchTerm = params.get<String>()
        )
    }
    viewModel { params ->
        VolumePopupViewModel(
            connectionHelper = get(),
            playerId = params.get<PlayerId>()
        )
    }
    viewModel { params ->
        SearchViewModel(
            application = androidApplication(),
            playerId = params.get<PlayerId>()
        )
    }
    viewModel { params ->
        MainContentViewModel(
            application = androidApplication(),
            playerId = params.get<PlayerId>()
        )
    }
}