/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.ui.nowplaying

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.size.Size
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.slider.LabelFormatter
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.PlaybackButtonRequest
import de.maniac103.squeezeclient.databinding.FragmentNowplayingBinding
import de.maniac103.squeezeclient.databinding.PopupWindowVolumeBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.doOnTransitionCompleted
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.withRoundedCorners
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetFragment
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NowPlayingFragment : Fragment(), MenuProvider, InputBottomSheetFragment.SubmitListener {
    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val playlistFragment get() =
        childFragmentManager.findFragmentById(binding.playlistFragment.id) as? PlaylistFragment

    private lateinit var binding: FragmentNowplayingBinding
    private lateinit var playlistBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private var timeUpdateJob: Job? = null
    private var sliderDragUpdateJob: Job? = null
    private var currentVolume = 0
    private var isMuted = false

    fun collapseIfExpanded(): Boolean {
        if (
            playlistBottomSheetBehavior.isDraggable &&
            playlistBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        ) {
            playlistBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return true
        }
        if (binding.container.currentState == R.id.expanded) {
            binding.container.transitionToState(R.id.collapsed)
            return true
        }
        return false
    }

    fun expandIfNeeded() {
        if (binding.container.currentState == R.id.collapsed) {
            binding.container.transitionToState(R.id.expanded)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNowplayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (playlistFragment == null) {
            childFragmentManager.commit {
                add(binding.playlistFragment.id, PlaylistFragment.create(playerId))
            }
        }

        val playlistView = binding.playlistHandleWrapper
        playlistBottomSheetBehavior = BottomSheetBehavior.from(playlistView).apply {
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        playlistFragment?.scrollToCurrentPlaylistPosition()
                    }
                    binding.toolbar.invalidateMenu()
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    binding.playlistFragment.alpha = slideOffset
                }
            })

            if (isDraggable) {
                binding.playlistHandle.setOnClickListener {
                    state = if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                        BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        }

        applyInsetsToSpacer(binding.topInsetSpacer, ConstraintSet.TOP) { insets ->
            insets.top
        }
        applyInsetsToSpacer(binding.bottomInsetSpacer, ConstraintSet.BOTTOM) { insets ->
            insets.bottom
        }
        applyInsetsToSpacer(binding.leftInsetSpacer, ConstraintSet.START) { insets ->
            insets.left
        }
        applyInsetsToSpacer(binding.rightInsetSpacer, ConstraintSet.END) { insets ->
            insets.right
        }

        binding.toolbar.apply {
            setNavigationOnClickListener {
                if (playlistBottomSheetBehavior.isDraggable) {
                    playlistBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
                binding.container.transitionToState(R.id.collapsed)
            }
            addMenuProvider(this@NowPlayingFragment)
        }

        binding.container.doOnTransitionCompleted {
            binding.toolbar.invalidateMenu()
        }

        // TODO: context menu if present

        binding.progressSlider.apply {
            labelBehavior = LabelFormatter.LABEL_GONE
            valueFrom = 0F
            addOnChangeListener { _, value, fromUser ->
                binding.elapsedTime.text = DateUtils.formatElapsedTime(value.toLong())
                if (fromUser) {
                    sliderDragUpdateJob?.cancel()
                    sliderDragUpdateJob = lifecycleScope.launch {
                        delay(200.milliseconds)
                        connectionHelper.updatePlaybackPosition(playerId, value.toInt())
                    }
                }
            }
        }
        binding.progressMinimized.apply {
            min = 0
        }

        bindToRequest(binding.repeat, PlaybackButtonRequest.ToggleRepeat(playerId))
        bindToRequest(binding.shuffle, PlaybackButtonRequest.ToggleShuffle(playerId))
        bindToRequest(binding.prev, PlaybackButtonRequest.PreviousTrack(playerId))
        bindToRequest(binding.next, PlaybackButtonRequest.NextTrack(playerId))

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.playStatus }
                    .collect { status ->
                        update(status)
                        currentVolume = status.currentVolume
                        isMuted = status.muted
                    }
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (resources.getBoolean(R.bool.nowplaying_playlist_always_open)) {
            playlistBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            playlistBottomSheetBehavior.isDraggable = false
            binding.playlistHandle.isInvisible = true
            binding.playlistFragment.alpha = 1F
        } else {
            playlistBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        val playlistExpanded = binding.container.currentState == R.id.expanded &&
            playlistBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        if (!playlistExpanded || !playlistBottomSheetBehavior.isDraggable) {
            menuInflater.inflate(R.menu.now_playing_menu, menu)
        }
        if (playlistExpanded) {
            menuInflater.inflate(R.menu.playlist_menu, menu)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.volume -> {
            showVolumePopup()
            true
        }
        R.id.save_playlist -> {
            val dummyAction = JiveAction.createEmptyForInput()
            val dummyInput = JiveActions.Input(
                1,
                null,
                null,
                JiveActions.Input.Type.Text,
                dummyAction,
                false
            )
            val title = getString(R.string.playlist_save_title)
            val f = InputBottomSheetFragment.create(title, dummyInput)
            f.show(childFragmentManager, "playlist_name")
            true
        }
        R.id.clear_playlist -> {
            lifecycleScope.launch {
                connectionHelper.clearCurrentPlaylist(playerId)
            }
            true
        }
        else -> false
    }

    override fun onInputSubmitted(title: String, action: JiveAction, isGoAction: Boolean): Job {
        val name = action.params.keys.first()
        return lifecycleScope.launch {
            connectionHelper.saveCurrentPlaylist(playerId, name)
        }
    }

    private fun applyInsetsToSpacer(spacer: View, side: Int, insetSelector: (Insets) -> Int) {
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val inset = insetSelector(insets)
            listOf(R.id.collapsed, R.id.expanded).forEach { setId ->
                binding.container.getConstraintSet(setId).apply {
                    setMargin(v.id, side, inset)
                }
            }
            windowInsets
        }
    }

    private fun update(status: PlayerStatus) {
        val currentSong = status.playlist.nowPlaying

        if (currentSong == null) {
            binding.container.transitionToState(R.id.collapsed)
            binding.container.isInteractionEnabled = false
        } else {
            binding.container.isInteractionEnabled = true
        }

        binding.artwork.load(currentSong?.extractIconUrl(requireContext())) {
            withRoundedCorners(binding.artwork)
            // TODO: placeholder?
            size(Size.ORIGINAL)
        }
        listOf(binding.title, binding.titleMinimized).forEach {
            it.text = currentSong?.title ?: getString(R.string.nowplaying_empty_playlist)
        }
        listOf(binding.artist, binding.artistMinimized).forEach {
            it.text = currentSong?.artist
            it.isVisible = !currentSong?.artist.isNullOrEmpty()
        }
        listOf(binding.titleMinimized, binding.artistMinimized).forEach {
            it.isEnabled = status.powered
        }
        binding.album.apply {
            text = currentSong?.album
            isVisible = !currentSong?.album.isNullOrEmpty()
        }

        timeUpdateJob?.cancel()
        if (status.currentSongDuration != null) {
            binding.progressSlider.apply {
                valueTo = max(
                    status.currentSongDuration.toDouble(DurationUnit.SECONDS).toFloat(),
                    0.1F
                )
                value = status.currentPlayPosition?.toDouble(DurationUnit.SECONDS)?.toFloat() ?: 0F
                isEnabled = true
            }
            binding.totalTime.text =
                DateUtils.formatElapsedTime(status.currentSongDuration.toLong(DurationUnit.SECONDS))
            if (status.playbackStartTimestamp != null) {
                timeUpdateJob = lifecycleScope.launch {
                    while (true) {
                        delay(1.seconds)
                        val positionSeconds = status.currentPlayPosition?.inWholeSeconds ?: 0F
                        // Duration is a float, but we increment in full seconds, thus it can happen
                        // the calculated position becomes larger than the end position, which Slider
                        // does not like.
                        binding.progressSlider.value =
                            positionSeconds.toFloat().coerceAtMost(binding.progressSlider.valueTo)
                        binding.progressMinimized.progress = positionSeconds.toInt()
                    }
                }
            } else {
                binding.progressSlider.value = 0F
                binding.progressMinimized.progress = 0
                binding.elapsedTime.text = DateUtils.formatElapsedTime(0)
            }
            binding.progressMinimized.apply {
                max = status.currentSongDuration.toInt(DurationUnit.SECONDS)
                progress = status.currentPlayPosition?.toInt(DurationUnit.SECONDS) ?: 0
            }
        } else {
            binding.progressSlider.apply {
                value = 0F
                valueTo = 0.1F
                isEnabled = false
            }
            binding.progressMinimized.apply {
                max = 0
                progress = 0
            }
        }

        binding.toolbar.subtitle = getString(
            R.string.nowplaying_subtitle,
            status.playerName,
            status.playlist.currentPosition,
            status.playlist.trackCount
        )

        binding.prev.isEnabled = status.playlist.currentPosition > 1
        binding.next.isEnabled = status.playlist.currentPosition < status.playlist.trackCount
        binding.playPause.isEnabled = currentSong != null
        binding.repeat.isEnabled = status.powered
        binding.shuffle.isEnabled = status.powered

        binding.shuffle.setImageResource(
            when (status.shuffleState) {
                PlayerStatus.ShuffleState.Off -> R.drawable.ic_shuffle_off_24dp
                PlayerStatus.ShuffleState.ShuffleAlbum -> R.drawable.ic_shuffle_album_24dp
                PlayerStatus.ShuffleState.ShuffleSong -> R.drawable.ic_shuffle_song_24dp
            }
        )
        binding.repeat.setImageResource(
            when (status.repeatState) {
                PlayerStatus.RepeatState.Off -> R.drawable.ic_repeat_off_24dp
                PlayerStatus.RepeatState.RepeatTitle -> R.drawable.ic_repeat_one_24dp
                PlayerStatus.RepeatState.RepeatAll -> R.drawable.ic_repeat_24dp
            }
        )

        val isPlaying = status.playbackState == PlayerStatus.PlayState.Playing
        val playPauseIconResId = when {
            isPlaying -> R.drawable.ic_pause_24dp
            else -> R.drawable.ic_play_24dp // TODO: selector drawable, power state?
        }
        binding.playPause.setImageResource(playPauseIconResId)
        binding.playPauseWrapper.setOnClickListener {
            lifecycleScope.launch {
                val targetState =
                    if (isPlaying) PlayerStatus.PlayState.Paused else PlayerStatus.PlayState.Playing
                connectionHelper.changePlaybackState(playerId, targetState)
            }
        }
    }

    private fun bindToRequest(view: View, request: PlaybackButtonRequest) {
        view.setOnClickListener {
            lifecycleScope.launch {
                connectionHelper.sendButtonRequest(request)
            }
        }
    }

    private fun showVolumePopup() {
        val popupBinding = PopupWindowVolumeBinding.inflate(layoutInflater)
        popupBinding.volumeSlider.apply {
            progress = currentVolume
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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
        }

        popupBinding.volumeMute.apply {
            isActivated = isMuted
            setOnClickListener {
                isActivated = !isActivated
                lifecycleScope.launch {
                    connectionHelper.setMuteState(playerId, isActivated)
                }
            }
        }

        popupBinding.root.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val popupMargin = resources.getDimensionPixelSize(R.dimen.volume_popup_end_margin)
        val popupWidth = popupBinding.root.measuredWidth
        val popupOffset = -(popupWidth + popupMargin)

        PopupWindow(
            popupBinding.root,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            showAsDropDown(binding.toolbar, popupOffset, 0, Gravity.END)
        }
    }

    companion object {
        fun create(playerId: PlayerId) = NowPlayingFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
