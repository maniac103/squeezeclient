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

package de.maniac103.squeezeclient.ui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.isHourInputValid
import androidx.compose.material3.isInputValid
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.extfuncs.invokeOnDoneOrNull
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun InputTimePicker(
    title: String,
    input: JiveActions.Input,
    onInputSubmitted: (JiveAction) -> Job?,
    onClose: () -> Unit
) {
    val (hour, minute) = input.initialText?.toIntOrNull()?.let { (it / 3600) to ((it / 60) % 60) }
        ?: (Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour to 0)
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute
    )
    TimePickerDialog(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        },
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(
                enabled = state.isInputValid,
                onClick = {
                    state.isHourInputValid
                    val seconds = state.hour * 3600 + state.minute * 60
                    val action = input.action.withInputValue(seconds.toString())
                    onInputSubmitted(action).let { job ->
                        job.invokeOnDoneOrNull(onClose)
                    }
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        modeToggleButton = {}
    ) {
        TimePicker(state = state)
    }
}
