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

import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.FragmentVolumeBinding
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.volumeStepSize
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class VolumeFragment : ViewBindingFragment<FragmentVolumeBinding>(FragmentVolumeBinding::inflate) {
    private val viewModel by viewModel<VolumePopupViewModel> {
        val playerId = requireArguments().getParcelable("playerId", PlayerId::class)
        parametersOf(playerId)
    }

    private var hideJob: Job? = null

    override fun onBindingCreated(binding: FragmentVolumeBinding) {
        binding.background.setOnClickListener {
            hideImmediately()
        }

        binding.compose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    val volume by viewModel.volumeFlow.collectAsState()
                    val muted by viewModel.mutedFlow.collectAsState()
                    VolumeControlPopup(
                        volume = volume,
                        muted = muted,
                        onVolumeChanged = viewModel::setVolume,
                        onMuteChanged = viewModel::setMuted
                    )
                }
            }
        }
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (!viewModel.volumeControlSupported) {
            return false
        }
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> prefs.volumeStepSize / 100F
            KeyEvent.KEYCODE_VOLUME_DOWN -> prefs.volumeStepSize / 100F
            else -> null
        } ?: return false

        showIfNeeded()
        viewModel.adjustVolume(delta)

        return true
    }

    fun handleKeyUp(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    fun showIfNeeded(duration: Duration = 2.seconds) {
        if (!isVisible && viewModel.volumeControlSupported) {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.volume_slide_in, R.animator.volume_slide_out)
                .show(this)
                .commitNow()
        }
        scheduleHide(duration)
    }

    private fun scheduleHide(duration: Duration) {
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(duration)
            hideImmediately()
        }
    }

    private fun hideImmediately() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.volume_slide_in, R.animator.volume_slide_out)
            .hide(this@VolumeFragment)
            .commitNowAllowingStateLoss()
    }

    companion object {
        fun create(playerId: PlayerId) = VolumeFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
            }
        }
    }
}

@Composable
fun VolumeControlPopup(
    volume: Float,
    muted: Boolean,
    onVolumeChanged: (Float) -> Unit,
    onMuteChanged: (Boolean) -> Unit
) {
    val sliderState = rememberSliderState(volume)

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 8.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.4F))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8F),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(4.dp)
            ) {
                VerticalSlider(
                    state = sliderState,
                    onValueChange = { sliderState.value = it },
                    onValueChangeFinished = { onVolumeChanged(sliderState.value) },
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
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_volume_muted_24dp),
                        contentDescription = null // FIXME
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.6F))
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun VolumeControlPopupPreview() {
    VolumeControlPopup(
        volume = 0.6F,
        muted = true,
        onVolumeChanged = {},
        onMuteChanged = {}
    )
}