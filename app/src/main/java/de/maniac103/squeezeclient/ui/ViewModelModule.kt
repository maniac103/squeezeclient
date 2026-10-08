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
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetViewModel
import de.maniac103.squeezeclient.ui.nowplaying.NowPlayingViewModel
import de.maniac103.squeezeclient.ui.nowplaying.PlaylistViewModel
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
}