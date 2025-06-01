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

package de.maniac103.squeezeclient.ui.volume

import android.view.KeyEvent
import android.widget.SeekBar
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.FragmentVolumeBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class VolumeFragment : ViewBindingFragment<FragmentVolumeBinding>(FragmentVolumeBinding::inflate) {
    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)

    private var currentPlayerVolume = 0
    private var isMuted = false
    private var hideJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onBindingCreated(binding: FragmentVolumeBinding) {
        binding.background.setOnClickListener {
            hideImmediately()
        }

        binding.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var updateJob: Job? = null
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateJob?.cancel()
                    updateJob = lifecycleScope.launch {
                        delay(200.milliseconds)
                        connectionHelper.setVolume(playerId, progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        binding.volumeMute.apply {
            setOnClickListener {
                isMuted = !isMuted
                lifecycleScope.launch {
                    connectionHelper.setMuteState(playerId, isMuted)
                    updateUiFromState()
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.playStatus }
                    .collect { status ->
                        currentPlayerVolume = status.currentVolume
                        isMuted = status.muted
                        updateUiFromState()
                    }
            }
        }
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        val volumeDelta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 5
            KeyEvent.KEYCODE_VOLUME_DOWN -> -5
            else -> null
        } ?: return false

        showIfNeeded()
        lifecycleScope.launch {
            currentPlayerVolume += volumeDelta
            connectionHelper.setVolume(playerId, currentPlayerVolume)
            updateUiFromState()
        }
        return true
    }

    fun handleKeyUp(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    fun showIfNeeded(duration: Duration = 2.seconds) {
        if (!isVisible) {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.volume_slide_in, R.animator.volume_slide_out)
                .show(this)
                .commitNow()
        }
        scheduleHide(duration)
    }

    private fun scheduleHide(duration: Duration) {
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(duration)
            hideImmediately()
        }
    }

    private fun hideImmediately() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.animator.volume_slide_in, R.animator.volume_slide_out)
            .hide(this@VolumeFragment)
            .commitNowAllowingStateLoss()
    }

    private fun updateUiFromState() {
        binding.volumeSlider.progress = currentPlayerVolume
        binding.volumeMute.isActivated = isMuted
    }

    companion object {
        fun create(playerId: PlayerId) = VolumeFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
