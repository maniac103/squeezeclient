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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.getParcelableOrNull
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job

class InputBottomSheetFragment : BaseBottomSheet() {
    interface ItemSubmitListener {
        fun onInputSubmitted(
            item: SlimBrowseItemList.SlimBrowseItem,
            action: JiveAction,
            isGoAction: Boolean
        ): Job?
    }
    interface InputSubmitListener {
        fun onInputSubmitted(title: String, action: JiveAction, isGoAction: Boolean): Job?
    }
    interface PlainSubmitListener {
        fun onInputSubmitted(value: String): Job?
    }

    override val title get() = parentItem?.title ?: requireArguments().getString("parentTitle")!!
    private val parentItem get() =
        requireArguments().getParcelableOrNull("item", SlimBrowseItemList.SlimBrowseItem::class)
    private val input get() = requireArguments().getParcelable("input", JiveActions.Input::class)

    @Composable
    override fun createContent() = Column {
        val textState = rememberTextFieldState(input.initialText ?: "")
        val busy by busyFlow.collectAsState()
        val errorMessage by remember {
            derivedStateOf { determineErrorState(textState.text, input) }
        }

        OutlinedTextField(
            state = textState,
            lineLimits = TextFieldLineLimits.SingleLine,
            enabled = !busy,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            onKeyboardAction = {
                if (errorMessage != null) {
                    submitInput(textState.text.toString())
                }
            },
            supportingText = {
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
        )

        FilledTonalButton(
            onClick = {
                submitInput(textState.text.toString())
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send_24dp),
                contentDescription = null // FIXME
            )
            Text(
                text = stringResource(R.string.input_submit)
            )
        }
    }

    private fun submitInput(inputText: String) {
        val job = when (val parent = parentFragment ?: activity) {
            is ItemSubmitListener -> {
                val action = input.action.withInputValue(inputText)
                parent.onInputSubmitted(requireNotNull(parentItem), action, input.actionHasTarget)
            }

            is InputSubmitListener -> {
                val action = input.action.withInputValue(inputText)
                parent.onInputSubmitted(title, action, input.actionHasTarget)
            }

            is PlainSubmitListener -> {
                parent.onInputSubmitted(inputText)
            }

            else -> null
        }
        handleAction(job, true)
    }

    private fun determineErrorState(text: CharSequence, input: JiveActions.Input) = when {
        text.length < input.minLength ->
            resources.getQuantityString(
                R.plurals.input_length_error_message,
                input.minLength,
                input.minLength
            )

        !input.allowedChars.isNullOrEmpty() && text.any { c -> !input.allowedChars.contains(c) } ->
            getString(R.string.input_character_error_message, input.allowedChars)

        else -> null
    }

    companion object {
        fun createForItem(item: SlimBrowseItemList.SlimBrowseItem, input: JiveActions.Input) =
            InputBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("item", item)
                    putParcelable("input", input)
                }
            }

        fun createPlain(
            title: String,
            minLength: Int = 0,
            initialText: String? = null,
            allowedChars: String? = null,
            type: JiveActions.Input.Type = JiveActions.Input.Type.Text
        ): InputBottomSheetFragment {
            val dummyAction = JiveAction.createEmptyForInput()
            val dummyInput = JiveActions.Input(
                minLength,
                initialText,
                allowedChars,
                type,
                dummyAction,
                false
            )
            return createForInput(title, dummyInput)
        }

        fun createForInput(parentTitle: String, input: JiveActions.Input) =
            InputBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString("parentTitle", parentTitle)
                    putParcelable("input", input)
                }
            }
    }
}
