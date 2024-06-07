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
import kotlin.time.Duration

sealed class ChangePlaybackStateRequest(playerId: PlayerId, vararg cmd: String) :
    NonPagedPlayerRequest(playerId, *cmd) {
    class Play(playerId: PlayerId, fadeInDuration: Duration) : ChangePlaybackStateRequest(
        playerId,
        "play",
        fadeInDuration.inWholeSeconds.let { if (it > 0) " $it" else "" }
    )
    class Pause(playerId: PlayerId) : ChangePlaybackStateRequest(playerId, "pause", "1")
    class Unpause(playerId: PlayerId, fadeInDuration: Duration) : ChangePlaybackStateRequest(
        playerId,
        "pause",
        "0",
        fadeInDuration.inWholeSeconds.let { if (it > 0) " $it" else "" }
    )
    class Stop(playerId: PlayerId) : ChangePlaybackStateRequest(playerId, "stop")
}
