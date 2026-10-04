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
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.maniac103.squeezeclient.ui.composables.BottomSheetContentWrapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

class InputBottomSheetFragment : BottomSheetDialogFragment() {
    interface SubmitListener {
        fun onInputSubmitted(value: String): Job?
    }

    private val title get() = requireArguments().getString("title")!!
    private val minLength get() = requireArguments().getInt("minLength")
    private val busyFlow = MutableStateFlow(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(inflater.context).apply {
        setContent {
            MaterialTheme {
                val busy by busyFlow.collectAsState()
                Column {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                    )
                    BottomSheetContentWrapper(title, busy) {
                        InputSheetContent(
                            minLength = minLength,
                            busy = busy,
                            onSubmit = { value -> submitInput(value) }
                        )
                    }
                }
            }
        }
    }

    private fun submitInput(inputText: String) {
        val listener = (parentFragment ?: activity) as? SubmitListener
        val job = listener?.onInputSubmitted(inputText)
        if (job != null) {
            busyFlow.value = true
            job.invokeOnCompletion {
                busyFlow.value = false
                dismissAllowingStateLoss()
            }
        } else {
            dismissAllowingStateLoss()
        }
    }

    companion object {
        fun create(title: String, minLength: Int = 0) = InputBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putInt("minLength", minLength)
            }
        }
    }
}