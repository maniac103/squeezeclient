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
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import de.maniac103.squeezeclient.databinding.BottomSheetContentChoicesBinding
import de.maniac103.squeezeclient.databinding.ListItemChoiceRadioBinding
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import kotlinx.coroutines.Job

class ChoicesBottomSheetFragment :
    BaseBottomSheet<BottomSheetContentChoicesBinding>(BottomSheetContentChoicesBinding::inflate) {
    interface SelectionListener {
        fun onChoiceSelected(choice: JiveAction, extraData: Bundle?): Job?
    }

    override val title get() = requireArguments().getString("parentTitle")!!
    private val choices get() =
        requireArguments().getParcelable("choices", JiveActions.Choices::class)
    private val extraData get() = requireArguments().getBundle("extra")
    private val listener get() = requireParentAs<SelectionListener>()

    override fun onContentInflated(content: BottomSheetContentChoicesBinding) {
        val inflater = layoutInflater
        content.radioGroup.apply {
            choices.items.forEachIndexed { index, item ->
                val radio = ListItemChoiceRadioBinding.inflate(inflater, this, false).root.apply {
                    text = item.title
                    id = index
                }
                addView(radio)
            }
            check(choices.selectedIndex)
            setOnCheckedChangeListener { _, index ->
                val job = listener.onChoiceSelected(choices.items[index].action, extraData)
                handleAction(job, true)
            }
        }
    }

    override fun onIndicateBusyState(content: BottomSheetContentChoicesBinding, busy: Boolean) {
        super.onIndicateBusyState(content, busy)
        content.radioGroup.forEach { it.isEnabled = !busy }
    }

    companion object {
        fun create(parentTitle: String, choices: JiveActions.Choices, extraData: Bundle? = null) =
            ChoicesBottomSheetFragment().apply {
                arguments = bundleOf(
                    "parentTitle" to parentTitle,
                    "choices" to choices,
                    "extra" to extraData
                )
            }
    }
}
