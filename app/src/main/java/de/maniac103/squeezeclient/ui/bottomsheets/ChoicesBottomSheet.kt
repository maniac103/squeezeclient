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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.ui.composables.BottomSheetContentWrapper
import kotlinx.coroutines.Job

@Composable
fun ChoicesBottomSheet(
    title: String,
    choices: JiveActions.Choices,
    onChoiceSelected: (JiveAction) -> Job?
) {
    var busy by remember { mutableStateOf(false) }
    BottomSheetContentWrapper(title, busy) {
        Column {
            choices.items.forEachIndexed { index, choice ->
                Row {
                    RadioButton(
                        selected = index == choices.selectedIndex,
                        enabled = !busy,
                        onClick = {
                            onChoiceSelected(choice.action)?.let { job ->
                                busy = true
                                job.invokeOnCompletion {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Text(
                        text = choice.title,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}
