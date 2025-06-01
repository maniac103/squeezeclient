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

package de.maniac103.squeezeclient.ui

import android.view.ViewGroup.MarginLayoutParams
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.databinding.FragmentDisplaystatusBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.loadArtworkOrPlaceholder
import de.maniac103.squeezeclient.model.DisplayMessage
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DisplayStatusFragment :
    ViewBindingFragment<FragmentDisplaystatusBinding>(FragmentDisplaystatusBinding::inflate) {
    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)

    private var hideJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onBindingCreated(binding: FragmentDisplaystatusBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = insets.bottom
            }
            windowInsets
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.displayStatus }
                    .collect { message -> showMessage(message) }
            }
        }
    }

    private fun showMessage(message: DisplayMessage) {
        if (message.type != DisplayMessage.MessageType.PopupPlay) {
            return
        }

        binding.text.text = message.text.joinToString("\n").trim()
        binding.icon.loadArtworkOrPlaceholder(message)
        binding.icon.isGone = message.extractIconUrl(requireContext()) == null

        if (!isVisible) {
            parentFragmentManager.commitNow {
                show(this@DisplayStatusFragment)
            }
        }

        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(message.duration ?: 2.seconds)
            parentFragmentManager.commitNow(true) {
                hide(this@DisplayStatusFragment)
            }
        }
    }

    companion object {
        fun create(playerId: PlayerId) = DisplayStatusFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
