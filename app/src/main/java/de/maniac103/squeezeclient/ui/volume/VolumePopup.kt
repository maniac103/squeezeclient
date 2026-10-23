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

package de.maniac103.squeezeclient.ui.volume

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.ui.composables.ResourceIcon
import kotlin.math.roundToInt

@Composable
fun VolumePopup(viewModel: VolumePopupViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val visible by viewModel.visibleFlow.collectAsStateWithLifecycle()

    state?.let {
        VolumePopup(
            volume = it.volume,
            muted = it.muted,
            visible = visible,
            onVolumeChanged = viewModel::setVolume,
            onMuteChanged = viewModel::setMuted
        )
    }
}

@Composable
fun VolumePopup(
    volume: Int,
    muted: Boolean,
    visible: Boolean,
    onVolumeChanged: (Int) -> Unit,
    onMuteChanged: (Boolean) -> Unit
) {
    val sliderState = rememberSliderState(
        value = volume.toFloat(),
        trackRange = 0F..100F
    )

    LaunchedEffect(volume) {
        sliderState.value = volume.toFloat()
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 8.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.4F))

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8F)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    VerticalSlider(
                        state = sliderState,
                        onValueChange = { sliderState.value = it },
                        onValueChangeFinished = { onVolumeChanged(sliderState.value.roundToInt()) },
                        track = { state ->
                            SliderDefaults.Track(
                                state,
                                trackCornerSize = 8.dp,
                                drawStopIndicator = null,
                                colors = SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.width(32.dp)
                            )
                        },
                        topToBottom = false,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .height(256.dp)
                    )

                    FilledIconToggleButton(
                        checked = muted,
                        onCheckedChange = onMuteChanged,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        ResourceIcon(
                            drawableResId = R.drawable.ic_volume_muted_24dp,
                            contentDescriptionResId = when (muted) {
                                true -> R.string.content_description_unmute
                                false -> R.string.content_description_mute
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.6F))
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun VolumePopupPreview() {
    VolumePopup(
        volume = 60,
        muted = true,
        visible = true,
        onVolumeChanged = {},
        onMuteChanged = {}
    )
}
