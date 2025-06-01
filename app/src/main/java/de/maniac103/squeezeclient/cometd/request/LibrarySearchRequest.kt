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

package de.maniac103.squeezeclient.cometd.request

import android.os.Parcelable
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.parcelize.Parcelize

class LibrarySearchRequest(playerId: PlayerId, searchTerm: String, mode: Mode, page: PagingParams) :
    Request(playerId, page, "browselibrary", "items") {
    init {
        params["mode"] = mode.cmd
        params["search"] = searchTerm
        params["menu"] = "browselibrary"
        params["useContextMenu"] = "1"
    }

    @Parcelize
    sealed class Mode(val cmd: String) : Parcelable {
        class Artist : Mode("artists")
        class Tracks : Mode("tracks")
        class Albums : Mode("albums")
        class Genres : Mode("genres")
    }
}
