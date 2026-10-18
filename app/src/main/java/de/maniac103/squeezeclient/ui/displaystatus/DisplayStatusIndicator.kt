/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024-2026 Danny Baumann
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.androidx.compose.koinViewModel

@Composable
fun DisplayStatusIndicator(serverConfig: ServerConfiguration?) {
    val viewModel = koinViewModel<DisplayStatusViewModel>()
    DisplayStatusIndicator(
        messageFlow = viewModel.messageFlow,
        serverConfig = serverConfig
    )
}

@Composable
fun DisplayStatusIndicator(
    messageFlow: Flow<DisplayStatusMessage?>,
    serverConfig: ServerConfiguration?
) {
    val message by messageFlow.collectAsStateWithLifecycle(null)

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding + PaddingValues(bottom = 8.dp))
            ) {
                val messageToShow = message
                if (messageToShow != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(8.dp)
                    ) {
                        messageToShow.icon?.let {
                            ArtworkImage(
                                artwork = it,
                                serverConfig = serverConfig,
                                usePlaceholder = false,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                        Text(
                            text = messageToShow.text,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun DisplayStatusIndicatorPreview() {
    val message = DisplayStatusMessage(
        text = "Some text\nSome additional text",
        icon = object : ArtworkItem {
            override val icon = null
            override val iconId = null
        }
    )
    DisplayStatusIndicator(flowOf(message), null)
}
