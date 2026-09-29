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

package de.maniac103.squeezeclient.ui

import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import de.maniac103.squeezeclient.databinding.FragmentComposeBinding
import de.maniac103.squeezeclient.extfuncs.addServerCredentialsIfNeeded
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.DisplayMessage
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class DisplayStatusFragment :
    ViewBindingFragment<FragmentComposeBinding>(FragmentComposeBinding::inflate) {
    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)

    private var hideJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageFlow by lazy {
        connectionHelper.playerState(playerId)
            .flatMapLatest { it.displayStatus }
            .filter { it.type != DisplayMessage.MessageType.PopupPlay }
    }

    override fun onBindingCreated(binding: FragmentComposeBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = insets.bottom
            }
            windowInsets
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                messageFlow.collect { show(it.duration) }
            }
        }
    }

    private fun show(duration: Duration?) {
        if (!isVisible) {
            parentFragmentManager.commitNow {
                show(this@DisplayStatusFragment)
            }
        }

        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(duration ?: 2.seconds)
            parentFragmentManager.commitNow(true) {
                hide(this@DisplayStatusFragment)
            }
        }
    }

    companion object {
        fun create(playerId: PlayerId) = DisplayStatusFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
            }
        }
    }
}

@Composable
fun DisplayStatusIndicator(
    messageFlow: Flow<DisplayMessage>,
    serverConfig: ServerConfiguration?
) {
    val message by messageFlow.collectAsState(null)
    val messageToShow = message ?: return

    Scaffold(
        contentColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding + PaddingValues(bottom = 8.dp))
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(8.dp)
            ) {
                messageToShow.extractIconUrl(serverConfig)?.let { iconUrl ->
                    val iconRequest = ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .addServerCredentialsIfNeeded(LocalContext.current)
                        .build()

                    AsyncImage(
                        model = iconRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.CenterVertically)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Text(
                    text = messageToShow.text.joinToString("\n").trim(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun DisplayStatusIndicatorPreview() {
    val message = DisplayMessage(
        DisplayMessage.MessageType.Text,
        null,
        false,
        null,
        listOf("Some text", "Some additional text"),
        null,
        null,
        null
    )
    DisplayStatusIndicator(flowOf(message), null)
}