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
import androidx.core.view.forEach
import de.maniac103.squeezeclient.databinding.BottomSheetContentChoicesBinding
import de.maniac103.squeezeclient.databinding.ListItemChoiceRadioBinding
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import kotlinx.coroutines.Job

class ChoicesBottomSheetFragment : BaseBottomSheet() {
    interface SelectionListener {
        fun onChoiceSelected(choice: JiveAction): Job?
    }

    override val title get() = requireArguments().getString("parentTitle")!!
    private val choices get() =
        requireArguments().getParcelable("choices", JiveActions.Choices::class)

    private lateinit var binding: BottomSheetContentChoicesBinding

    override fun onInflateContent(inflater: LayoutInflater, container: ViewGroup): View {
        binding = BottomSheetContentChoicesBinding.inflate(inflater, container, false)
        choices.items.forEachIndexed { index, item ->
            val radio = ListItemChoiceRadioBinding.inflate(inflater, binding.radioGroup, false)
                .root
            radio.text = item.title
            radio.id = index
            binding.radioGroup.addView(radio)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.radioGroup.apply {
            check(choices.selectedIndex)
            setOnCheckedChangeListener { _, index ->
                val listener = parentFragment as? SelectionListener
                    ?: activity as? SelectionListener
                val job = listener?.onChoiceSelected(choices.items[index].action)
                handleAction(job, true)
            }
        }
    }

    override fun onIndicateBusyState(busy: Boolean) {
        super.onIndicateBusyState(busy)
        binding.radioGroup.forEach { it.isEnabled = !busy }
    }

    companion object {
        fun create(parentTitle: String, choices: JiveActions.Choices) =
            ChoicesBottomSheetFragment().apply {
                arguments = bundleOf("parentTitle" to parentTitle, "choices" to choices)
            }
    }
}
