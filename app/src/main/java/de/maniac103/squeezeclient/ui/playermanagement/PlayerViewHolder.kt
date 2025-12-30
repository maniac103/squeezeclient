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

package de.maniac103.squeezeclient.ui.playermanagement

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.ListItemMasterPlayerBinding
import de.maniac103.squeezeclient.databinding.ListItemSlavePlayerBinding
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

@SuppressLint("ClickableViewAccessibility")
sealed class PlayerViewHolder(
    itemView: View,
    contentView: View,
    dragHandle: View,
    private val nameView: TextView,
    private val modelNameView: TextView,
    private val powerButton: ImageView,
    private val playStateButton: ImageView?,
    private val volumeSlider: Slider,
    private val callback: Callback
) : DragDropAwareViewHolder(itemView, contentView, dragHandle) {
    val playerId get() = lastBoundData?.playerId
    private var lastBoundData: PlayerData? = null
    private var pendingVolumeChange: Job? = null

    interface Callback {
        fun onVolumeChanged(playerId: PlayerId, volume: Int): Job?
        fun onPowerToggled(playerId: PlayerId): Job?
        fun onPlayStateChanged(playerId: PlayerId, playState: PlayerStatus.PlayState): Job?
        fun onDragInitiated(holder: PlayerViewHolder)
    }

    init {
        volumeSlider.apply {
            valueFrom = 0F
            valueTo = 100F
            setLabelFormatter { value -> "${value.roundToInt()}%" }
            addOnChangeListener { _, value, fromUser ->
                pendingVolumeChange?.cancel()
                if (fromUser) {
                    val data = lastBoundData ?: return@addOnChangeListener
                    pendingVolumeChange =
                        callback.onVolumeChanged(data.playerId, value.roundToInt())
                }
            }
        }
        playStateButton?.setOnClickListener {
            val data = lastBoundData ?: return@setOnClickListener
            val newState = if (data.playbackState == PlayerStatus.PlayState.Playing) {
                PlayerStatus.PlayState.Paused
            } else {
                PlayerStatus.PlayState.Playing
            }
            callback.onPlayStateChanged(data.playerId, newState)
        }
        playStateButton?.setOnLongClickListener {
            val data = lastBoundData ?: return@setOnLongClickListener false
            callback.onPlayStateChanged(data.playerId, PlayerStatus.PlayState.Stopped)
            true
        }
        powerButton.setOnClickListener {
            lastBoundData?.playerId?.let { callback.onPowerToggled(it) }
        }
        dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                callback.onDragInitiated(this)
            }
            false
        }
    }

    open fun bindTo(data: PlayerData) {
        lastBoundData = data
        nameView.text = data.name
        modelNameView.text = data.modelName
        playStateButton?.setImageResource(
            when (data.playbackState) {
                PlayerStatus.PlayState.Playing -> R.drawable.ic_pause_24dp
                else -> R.drawable.ic_play_24dp
            }
        )
        powerButton.isInvisible = !data.canPowerOff
        powerButton.alpha = if (data.powered) 1F else 0.5F
        volumeSlider.isInvisible = data.volume == null
        data.volume?.let { volumeSlider.value = it.toFloat() }
    }

    class MasterPlayerViewHolder(val binding: ListItemMasterPlayerBinding, callback: Callback) :
        PlayerViewHolder(
            binding.root,
            binding.content,
            binding.dragHandle.dragHandle,
            binding.name,
            binding.model,
            binding.power,
            binding.playState,
            binding.volume,
            callback
        ) {
        override fun bindTo(data: PlayerData) {
            super.bindTo(data)
            binding.nowplaying.isVisible = data.nowPlayingInfo != null
            binding.nowplaying.text = data.nowPlayingInfo?.let {
                "${it.title} · ${it.artist}"
            }
        }
    }

    class SlavePlayerViewHolder(val binding: ListItemSlavePlayerBinding, callback: Callback) :
        PlayerViewHolder(
            binding.root,
            binding.content,
            binding.dragHandle.dragHandle,
            binding.name,
            binding.model,
            binding.power,
            null,
            binding.volume,
            callback
        )
}
