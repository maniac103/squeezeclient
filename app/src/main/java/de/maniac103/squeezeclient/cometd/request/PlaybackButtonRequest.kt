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

import de.maniac103.squeezeclient.model.PlayerId

sealed class PlaybackButtonRequest(
    playerId: PlayerId,
    type: String
) : NonPagedPlayerRequest(playerId, "button", type) {
    class PreviousTrack(playerId: PlayerId) : PlaybackButtonRequest(playerId, "jump_rew")
    class NextTrack(playerId: PlayerId) : PlaybackButtonRequest(playerId, "jump_fwd")
    class ToggleShuffle(playerId: PlayerId) : PlaybackButtonRequest(playerId, "shuffle")
    class ToggleRepeat(playerId: PlayerId) : PlaybackButtonRequest(playerId, "repeat")
}
