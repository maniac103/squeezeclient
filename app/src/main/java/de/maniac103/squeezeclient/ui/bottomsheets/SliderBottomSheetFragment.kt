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

package de.maniac103.squeezeclient.ui.bottomsheets

import android.os.Bundle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Label
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

class SliderBottomSheetFragment : BaseBottomSheet() {
    interface ChangeListener {
        fun onSliderChanged(input: JiveAction): Job
    }

    override val title get() = requireArguments().getString("title")!!
    private val slider get() = requireArguments().getParcelable("slider", JiveActions.Slider::class)
    private val listener get() = requireParentAs<ChangeListener>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun createContent() {
        val busy by busyFlow.collectAsState()
        val sliderState = rememberSliderState(
            value = slider.initialValue.toFloat(),
            trackRange = slider.min.toFloat() .. slider.max.toFloat()
        )
        val interactionSource = remember { MutableInteractionSource() }

        Slider(
            state = sliderState,
            enabled = !busy,
            interactionSource = interactionSource,
            onValueChange = { sliderState.value = it },
            onValueChangeFinished = {
                val inputValue = sliderState.value.roundToInt().toString()
                val action = this@SliderBottomSheetFragment.slider.action.withInputValue(inputValue)
                val job = listener.onSliderChanged(action)
                handleAction(job, false)
            },
            thumb = {
                Label(
                    interactionSource = interactionSource,
                    label = {
                        PlainTooltip(
                            modifier = Modifier
                                .wrapContentSize()
                        ) {
                            Text(text = sliderState.value.roundToInt().toString())
                        }
                    }
                ) {
                    SliderDefaults.Thumb(interactionSource)
                }
            },
            modifier = Modifier
                .padding(16.dp)
        )
    }

    companion object {
        fun create(title: String, slider: JiveActions.Slider) = SliderBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putParcelable("slider", slider)
            }
        }
    }
}
