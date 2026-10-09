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

package de.maniac103.squeezeclient.ui.nowplaying

import android.os.Bundle
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.extfuncs.viewModelWithParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.common.ComposeFragment
import kotlin.time.ExperimentalTime
import kotlin.getValue

@OptIn(ExperimentalTime::class)
class PlaylistFragment : ComposeFragment() {
    private val viewModel by viewModelWithParams {
        val playerId = requireArguments().getParcelable("playerId", PlayerId::class)
        PlaylistViewModel(requireActivity().application.connectionHelper, playerId)
    }

    @Composable
    override fun createContent() = PlaylistItemColumn(
        viewModel,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        prefs.serverConfig,
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .scrollable(
                state = remember { ScrollableState(consumeScrollDelta = { 0f }) },
                orientation = Orientation.Vertical
            )
    )

    fun scrollToCurrentPlaylistPosition() {
        viewModel.scrollToCurrentPlaylistPosition()
    }

    companion object {
        fun create(playerId: PlayerId) = PlaylistFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
            }
        }
    }
}
