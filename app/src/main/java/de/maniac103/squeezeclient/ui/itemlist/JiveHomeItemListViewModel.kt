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

import androidx.lifecycle.ViewModel
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class JiveHomeItemListViewModel(
    connectionHelper: ConnectionHelper,
    playerId: PlayerId,
    val nodeId: String
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val homeMenuFlow = connectionHelper
        .playerState(playerId)
        .flatMapLatest { it.homeMenu }

    val homeMenuItemsFlow = homeMenuFlow.map { menu ->
        menu.values
            .filter { it.node == nodeId }
            .sortedBy { it.sortWeight }
            .map { it.toUiItem() }
    }
}
