/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.extfuncs

import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

val Fragment.connectionHelper get() = requireContext().connectionHelper

fun Fragment.showActionTimePicker(playerId: PlayerId, title: String, input: JiveActions.Input) {
    val (hour, minute) = input.initialText?.toIntOrNull()?.let { (it / 3600) to (it / 60) }
        ?: (Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour to 0)
    val timeFormat = if (DateFormat.is24HourFormat(context)) {
        TimeFormat.CLOCK_24H
    } else {
        TimeFormat.CLOCK_12H
    }
    val picker = MaterialTimePicker.Builder()
        .setTitleText(title)
        .setHour(hour)
        .setMinute(minute)
        .setTimeFormat(timeFormat)
        .build()
    picker.addOnPositiveButtonClickListener {
        lifecycleScope.launch {
            val seconds = picker.hour * 3600 + picker.minute * 60
            val action = input.action.withInputValue(seconds.toString())
            connectionHelper.executeAction(playerId, action)
        }
    }
    picker.show(childFragmentManager, "timepicker")
}
