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
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.BottomSheetContentInputBinding
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

    private lateinit var binding: BottomSheetContentInputBinding

    override fun onInflateContent(inflater: LayoutInflater, container: ViewGroup): View {
        binding = BottomSheetContentInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = input
        binding.editor.apply {
            doAfterTextChanged { text ->
                text?.let {
                    binding.editorWrapper.error = determineErrorState(text, input)
                    binding.sendButton.isEnabled = binding.editorWrapper.error == null
                }
            }
            setText(input.initialText)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND && error == null) {
                    submitInput(text.toString())
                    true
                } else {
                    false
                }
            }
        }
        binding.sendButton.setOnClickListener {
            binding.editor.text?.toString()?.let { submitInput(it) }
        }
    }

    override fun onIndicateBusyState(busy: Boolean) {
        super.onIndicateBusyState(busy)
        binding.sendButton.isEnabled = !busy
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
                arguments = bundleOf("item" to item, "input" to input)
            }

        fun createPlain(
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
            return createForInput("", dummyInput)
        }

        fun createForInput(parentTitle: String, input: JiveActions.Input) =
            InputBottomSheetFragment().apply {
                arguments = bundleOf("parentTitle" to parentTitle, "input" to input)
            }
    }
}
