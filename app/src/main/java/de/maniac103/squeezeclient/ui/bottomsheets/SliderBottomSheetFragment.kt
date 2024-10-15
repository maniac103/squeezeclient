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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.slider.Slider
import de.maniac103.squeezeclient.databinding.BottomSheetContentSliderBinding
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

class SliderBottomSheetFragment : BaseBottomSheet(), Slider.OnSliderTouchListener {
    interface ChangeListener {
        fun onSliderChanged(input: JiveAction): Job
    }

    override val title get() = requireArguments().getString("title")!!
    private val slider get() = requireArguments().getParcelable("slider", JiveActions.Slider::class)
    private val listener get() = requireParentAs<ChangeListener>()

    private lateinit var binding: BottomSheetContentSliderBinding

    override fun onInflateContent(inflater: LayoutInflater, container: ViewGroup): View {
        binding = BottomSheetContentSliderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val slider = slider
        binding.slider.apply {
            valueFrom = slider.min.toFloat()
            valueTo = slider.max.toFloat()
            value = slider.initialValue.toFloat()
            setLabelFormatter { value -> value.roundToInt().toString() }
            // TODO: icons
            addOnSliderTouchListener(this@SliderBottomSheetFragment)
        }
    }

    override fun onStartTrackingTouch(slider: Slider) {
    }

    override fun onStopTrackingTouch(slider: Slider) {
        val inputValue = slider.value.roundToInt().toString()
        val action = this@SliderBottomSheetFragment.slider.action.withInputValue(inputValue)
        val job = listener.onSliderChanged(action)
        handleAction(job, false)
    }

    override fun onIndicateBusyState(busy: Boolean) {
        super.onIndicateBusyState(busy)
        binding.slider.isEnabled = !busy
    }

    companion object {
        fun create(title: String, slider: JiveActions.Slider) = SliderBottomSheetFragment().apply {
            arguments = bundleOf("title" to title, "slider" to slider)
        }
    }
}
