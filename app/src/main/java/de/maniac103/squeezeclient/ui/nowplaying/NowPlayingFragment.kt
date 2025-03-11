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

package de.maniac103.squeezeclient.ui.nowplaying

import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.size.Size
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.slider.LabelFormatter
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.PlaybackButtonRequest
import de.maniac103.squeezeclient.databinding.FragmentNowplayingBinding
import de.maniac103.squeezeclient.extfuncs.backProgressInterpolator
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.doOnTransitionCompleted
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.loadArtwork
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetFragment
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetFragment
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class NowPlayingFragment :
    ViewBindingFragment<FragmentNowplayingBinding>(FragmentNowplayingBinding::inflate),
    MenuProvider,
    ContextMenuBottomSheetFragment.Listener,
    InputBottomSheetFragment.PlainSubmitListener {

    interface Listener {
        fun onContextMenuAction(
            action: JiveAction,
            parentItem: SlimBrowseItemList.SlimBrowseItem,
            contextItem: SlimBrowseItemList.SlimBrowseItem
        ): Job?
        fun showVolumePopup()
    }

    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val playlistFragment get() =
        childFragmentManager.findFragmentById(binding.playlistFragment.id) as? PlaylistFragment
    private val listener get() = requireParentAs<Listener>()

    private lateinit var playlistBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private var timeUpdateJob: Job? = null
    private var sliderDragUpdateJob: Job? = null
    private var currentSong: Playlist.PlaylistItem? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        private var startedCollapse = false

        override fun handleOnBackPressed() {
            when {
                canCollapsePlaylist() -> playlistBottomSheetBehavior.handleBackInvoked()
                sheetIsExpanded() || startedCollapse -> {
                    binding.container.transitionToState(R.id.collapsed)
                    startedCollapse = false
                }
            }
        }

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            when {
                canCollapsePlaylist() -> playlistBottomSheetBehavior.startBackProgress(backEvent)
                sheetIsExpanded() -> {
                    binding.container.setTransition(R.id.expanded, R.id.collapsed)
                    binding.container.progress = 0F
                    startedCollapse = true
                }
            }
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            when {
                canCollapsePlaylist() ->
                    playlistBottomSheetBehavior.updateBackProgress(backEvent)
                startedCollapse -> {
                    val progress = backProgressInterpolator.getInterpolation(backEvent.progress)
                    binding.container.progress = 0.2F * progress
                }
            }
        }

        override fun handleOnBackCancelled() {
            when {
                canCollapsePlaylist() -> playlistBottomSheetBehavior.cancelBackProgress()
                startedCollapse -> {
                    binding.container.setTransition(R.id.collapsed, R.id.expanded)
                    binding.container.progress = 1F
                    startedCollapse = false
                }
            }
        }
    }

    fun expandIfNeeded() {
        if (binding.container.currentState == R.id.collapsed) {
            binding.container.transitionToState(R.id.expanded)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onBindingCreated(binding: FragmentNowplayingBinding) {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )

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
                    updateBackPressedCallbackState()
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

        binding.topInsetSpacer.applyInsetsAsMargin(ConstraintSet.TOP) { it.top }
        binding.bottomInsetSpacer.applyInsetsAsMargin(ConstraintSet.BOTTOM) { it.bottom }
        binding.leftInsetSpacer.applyInsetsAsMargin(ConstraintSet.START) { it.left }
        binding.rightInsetSpacer.applyInsetsAsMargin(ConstraintSet.END) { it.right }

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
            updateBackPressedCallbackState()
            binding.toolbar.invalidateMenu()
        }

        binding.progressSlider.apply {
            labelBehavior = LabelFormatter.LABEL_GONE
            valueFrom = 0F
            addOnChangeListener { _, value, fromUser ->
                binding.elapsedTime.text = DateUtils.formatElapsedTime(value.toLong())
                if (fromUser) {
                    sliderDragUpdateJob?.cancel()
                    sliderDragUpdateJob = lifecycleScope.launch {
                        delay(200.milliseconds)
                        timeUpdateJob?.cancel()
                        connectionHelper.updatePlaybackPosition(playerId, value.toInt())
                    }
                }
            }
        }
        binding.progressMinimized.apply {
            min = 0
        }

        binding.repeat.bindToRequest(PlaybackButtonRequest.ToggleRepeat(playerId))
        binding.shuffle.bindToRequest(PlaybackButtonRequest.ToggleShuffle(playerId))
        binding.prev.bindToRequest(PlaybackButtonRequest.PreviousTrack(playerId))
        binding.next.bindToRequest(PlaybackButtonRequest.NextTrack(playerId))

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.playStatus }
                    .collect { status ->
                        update(status)
                        val nowPlaying = status.playlist.nowPlaying
                        if (nowPlaying != currentSong) {
                            currentSong = nowPlaying
                            binding.toolbar.invalidateMenu()
                        }
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

    // MenuProvider implementation

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

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        menu.findItem(R.id.info)?.setVisible(currentSong?.actions?.moreAction != null)
    }

    override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.volume -> {
            listener.showVolumePopup()
            true
        }
        R.id.info -> {
            val currentSong = currentSong
            currentSong?.actions?.moreAction?.let { action ->
                lifecycleScope.launch {
                    val items = connectionHelper.fetchItemsForAction(
                        playerId,
                        action,
                        PagingParams.All,
                        false
                    )
                    val contextMenu = ContextMenuBottomSheetFragment.create(
                        playerId,
                        currentSong.asSlimbrowseItem(),
                        items.items
                    )
                    contextMenu.show(childFragmentManager, "song_info")
                }
            }
            true
        }
        R.id.save_playlist -> {
            val f = InputBottomSheetFragment.createPlain(
                getString(R.string.playlist_save_title),
                minLength = 1
            )
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

    // InputBottomSheetFragment.PlainSubmitListener implementation

    override fun onInputSubmitted(value: String) = lifecycleScope.launch {
        connectionHelper.saveCurrentPlaylist(playerId, value)
    }

    // ContextMenuBottomSheetFragment.Listener implementation

    override fun onContextItemSelected(
        parentItem: SlimBrowseItemList.SlimBrowseItem,
        selectedItem: SlimBrowseItemList.SlimBrowseItem
    ): Job? {
        val actions = selectedItem.actions ?: return null
        val job = when {
            actions.doAction != null -> lifecycleScope.launch {
                connectionHelper.executeAction(playerId, actions.doAction)
            }
            actions.goAction != null -> {
                listener.onContextMenuAction(actions.goAction, parentItem, selectedItem)
            }
            else -> null
        }
        job?.invokeOnCompletion {
            collapseIfExpanded()
        }
        return job
    }

    // Private implementation details

    private fun View.applyInsetsAsMargin(side: Int, insetSelector: View.(Insets) -> Int) {
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            val inset = insetSelector(insets)
            listOf(R.id.collapsed, R.id.expanded).forEach { setId ->
                binding.container.getConstraintSet(setId).apply {
                    setMargin(v.id, side, inset)
                }
            }
            windowInsets
        }
    }

    private fun View.bindToRequest(request: PlaybackButtonRequest) = setOnClickListener {
        lifecycleScope.launch {
            connectionHelper.sendButtonRequest(request)
        }
    }

    private fun update(status: PlayerStatus) {
        val currentSong = status.playlist.nowPlaying

        if (currentSong == null) {
            binding.container.transitionToState(R.id.collapsed)
            binding.container.isInteractionEnabled = false
            binding.artwork.setImageDrawable(null)
        } else {
            binding.container.isInteractionEnabled = true
            binding.artwork.loadArtwork(currentSong) {
                fallback(R.drawable.ic_album_placeholder)
                size(Size.ORIGINAL)
            }
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
            binding.progressMinimized.apply {
                max = status.currentSongDuration.toInt(DurationUnit.SECONDS)
                progress = status.currentPlayPosition?.toInt(DurationUnit.SECONDS) ?: 0
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

    private fun updateBackPressedCallbackState() {
        onBackPressedCallback.isEnabled = canCollapsePlaylist() || sheetIsExpanded()
    }

    private fun collapseIfExpanded() = when {
        canCollapsePlaylist() ->
            playlistBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetIsExpanded() -> binding.container.transitionToState(R.id.collapsed)
        else -> {}
    }

    private fun canCollapsePlaylist() = playlistBottomSheetBehavior.isDraggable &&
        playlistBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED

    private fun sheetIsExpanded() = binding.container.currentState == R.id.expanded

    companion object {
        fun create(playerId: PlayerId) = NowPlayingFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
