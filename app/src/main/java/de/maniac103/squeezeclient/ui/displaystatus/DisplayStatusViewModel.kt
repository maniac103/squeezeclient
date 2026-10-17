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

package de.maniac103.squeezeclient.ui.displaystatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.DisplayMessage
import de.maniac103.squeezeclient.model.PlayerId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DisplayStatusViewModel(connectionHelper: ConnectionHelper, playerId: PlayerId) : ViewModel() {
    private val uiMessageFlowInternal = MutableStateFlow<DisplayStatusMessage?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val backendMessageFlow = connectionHelper.playerState(playerId)
        .flatMapLatest { it.displayStatus }
        .filter { it.type == DisplayMessage.MessageType.PopupPlay }

    val messageFlow = uiMessageFlowInternal.asStateFlow()

    init {
        viewModelScope.launch {
            backendMessageFlow.collectLatest { message ->
                uiMessageFlowInternal.emit(
                    DisplayStatusMessage(
                        text = message.text.joinToString("\n").trim(),
                        icon = message
                    )
                )
                delay(message.duration ?: 2.seconds)
                uiMessageFlowInternal.emit(null)
            }
        }
    }
}

class DisplayStatusMessage(val text: String, val icon: ArtworkItem)
