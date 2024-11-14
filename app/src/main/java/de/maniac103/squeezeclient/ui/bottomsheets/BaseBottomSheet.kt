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
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.maniac103.squeezeclient.databinding.BottomSheetBaseBinding
import de.maniac103.squeezeclient.ui.common.ViewBindingCreator
import kotlinx.coroutines.Job

abstract class BaseBottomSheet<VB : ViewBinding>(private val creator: ViewBindingCreator<VB>) :
    BottomSheetDialogFragment() {
    protected abstract val title: String
    private lateinit var binding: BottomSheetBaseBinding
    private lateinit var content: VB

    protected abstract fun onContentInflated(content: VB)

    protected open fun onIndicateBusyState(content: VB, busy: Boolean) {
        binding.progress.isVisible = busy
    }

    protected fun handleAction(job: Job?, dismissOnDone: Boolean) {
        if (job != null) {
            onIndicateBusyState(content, true)
            job.invokeOnCompletion {
                onIndicateBusyState(content, false)
                if (dismissOnDone) {
                    dismissAllowingStateLoss()
                }
            }
        } else if (dismissOnDone) {
            dismissAllowingStateLoss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = BottomSheetBaseBinding.inflate(inflater, container, false)
        content = creator(inflater, binding.container, false)
        binding.container.addView(content.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onContentInflated(content)
        binding.title.text = title
    }
}
