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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import kotlinx.coroutines.Job

internal sealed interface InputError {
    data class TooShort(val minLength: Int) : InputError
    data class InvalidCharacters(val allowedChars: String) : InputError
}

@Composable
fun InputBottomSheetContent(
    title: String,
    input: JiveActions.Input,
    onInputSubmitted: (JiveAction) -> Job?,
    modifier: Modifier = Modifier
) {
    var busy by remember { mutableStateOf(false) }
    InputBottomSheetContent(
        title = title,
        busy = busy,
        initialText = input.initialText,
        minLength = input.minLength,
        allowedChars = input.allowedChars,
        modifier = modifier,
        onSubmit = { value ->
            val action = input.action.withInputValue(value)
            onInputSubmitted(action)?.let { job ->
                busy = true
                job.invokeOnCompletion {
                    busy = false
                }
            }
        }
    )
}

@Composable
fun InputBottomSheetContent(
    title: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    initialText: String? = null,
    minLength: Int = 0,
    allowedChars: String? = null,
    onSubmit: (String) -> Unit = {}
) = BottomSheetContentWrapper(
    title = title,
    busy = busy
) {
    Column(
        modifier = modifier
    ) {
        val textState = rememberTextFieldState(initialText ?: "")
        val errorReason by remember {
            derivedStateOf {
                when {
                    textState.text.length < minLength -> InputError.TooShort(minLength)

                    !allowedChars.isNullOrEmpty() &&
                        textState.text.any { c -> !allowedChars.contains(c) } ->
                        InputError.InvalidCharacters(allowedChars)

                    else -> null
                }
            }
        }

        OutlinedTextField(
            state = textState,
            lineLimits = TextFieldLineLimits.SingleLine,
            enabled = !busy,
            isError = errorReason != null,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            onKeyboardAction = {
                if (errorReason != null) {
                    onSubmit(textState.text.toString())
                }
            },
            supportingText = {
                errorReason?.let { error ->
                    val message = when (error) {
                        is InputError.TooShort ->
                            pluralStringResource(
                                R.plurals.input_length_error_message,
                                error.minLength,
                                error.minLength
                            )

                        is InputError.InvalidCharacters ->
                            stringResource(
                                R.string.input_character_error_message,
                                error.allowedChars
                            )
                    }
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
        )

        FilledTonalButton(
            onClick = {
                onSubmit(textState.text.toString())
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.input_submit),
                modifier = Modifier.padding(end = 8.dp)
            )
            Icon(
                ImageVector.vectorResource(R.drawable.ic_send_24dp),
                contentDescription = null
            )
        }
    }
}
