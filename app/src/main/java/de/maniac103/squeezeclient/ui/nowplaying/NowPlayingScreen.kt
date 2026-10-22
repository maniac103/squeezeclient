/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

import android.text.format.DateUtils
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.bottomsheets.InputSheetContent
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.ui.composables.BottomSheetContentWrapper
import de.maniac103.squeezeclient.ui.composables.ResourceIcon
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton
import de.maniac103.squeezeclient.ui.composables.toTextResource
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetContent
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetViewModel
import de.maniac103.squeezeclient.ui.maincontent.MainLayout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun NowPlayingScreen(
    playerId: PlayerId,
    layout: MainLayout,
    motionState: NowPlayingMotionState,
    serverConfig: ServerConfiguration?,
    insets: PaddingValues,
    onShowVolumePopup: () -> Unit,
    onInfoMenuItemGoAction: (
        action: JiveAction,
        parentItem: SlimBrowseItemList.SlimBrowseItem,
        contextItem: SlimBrowseItemList.SlimBrowseItem
    ) -> Job?,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<NowPlayingViewModel> {
        parametersOf(playerId)
    }
    val playlistViewModel = koinViewModel<PlaylistViewModel> {
        parametersOf(playerId)
    }

    LaunchedEffect(motionState.playlist.isExpanded) {
        if (motionState.playlist.isExpanded) {
            playlistViewModel.scrollToCurrentPlaylistPosition()
        }
    }

    val scope = rememberCoroutineScope()

    NowPlayingScreen(
        playerId = playerId,
        layoutInput = layout.toGeometryInput(),
        stateFlow = viewModel.uiStateFlow,
        infoMenuFlow = viewModel.contextMenuFlow,
        playlistSaveInputStateFlow = viewModel.playlistSaveInputStateFlow,
        motionState = motionState,
        serverConfig = serverConfig,
        insets = insets,
        playlistContent = { surfaceColor, playlistModifier ->
            PlaylistItemColumn(
                viewModel = playlistViewModel,
                surfaceColor = surfaceColor,
                serverConfig = serverConfig,
                modifier = playlistModifier
            )
        },
        onPreviousTrack = viewModel::previous,
        onNextTrack = viewModel::next,
        onToggleRepeat = viewModel::toggleRepeat,
        onToggleShuffle = viewModel::toggleShuffle,
        onSetPlayingOrPaused = viewModel::setPlayPauseState,
        onStop = viewModel::stop,
        onSeek = viewModel::seekTo,
        onShowInfoMenu = viewModel::loadContextMenu,
        onInfoMenuItemSelected = { parentItem, contextItem ->
            val job = when (val result = viewModel.onContextMenuItemSelected(contextItem)) {
                is ContextMenuItemResult.None -> null

                is ContextMenuItemResult.DoAction -> result.job

                is ContextMenuItemResult.GoAction ->
                    onInfoMenuItemGoAction(result.action, parentItem, contextItem)
            }
            job?.invokeOnCompletion {
                viewModel.onContextMenuDismissed()
                scope.launch {
                    motionState.nowPlaying.collapse()
                }
            }
        },
        onInfoMenuDismissed = viewModel::onContextMenuDismissed,
        onShowSavePlaylistNameInput = viewModel::showPlaylistSaveInput,
        onSavePlaylistNameEntered = viewModel::savePlaylist,
        onSavePlaylistDismissed = viewModel::onPlaylistSaveInputDismissed,
        onClearPlaylist = viewModel::clearPlaylist,
        onShowVolumePopup = onShowVolumePopup,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingScreen(
    playerId: PlayerId,
    layoutInput: PlayerGeometryInput,
    stateFlow: StateFlow<NowPlayingUiState?>,
    infoMenuFlow: StateFlow<NowPlayingContextMenuState>,
    playlistSaveInputStateFlow: StateFlow<PlaylistSaveEditorState>,
    serverConfig: ServerConfiguration?,
    insets: PaddingValues,
    motionState: NowPlayingMotionState,
    playlistContent: @Composable (Color, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onSetPlayingOrPaused: (play: Boolean) -> Unit = {},
    onStop: () -> Unit = {},
    onSeek: (position: Int) -> Unit = {},
    onShowInfoMenu: (NowPlayingContextMenuData) -> Unit = {},
    onInfoMenuItemSelected: (
        parentItem: SlimBrowseItemList.SlimBrowseItem,
        contextItem: SlimBrowseItemList.SlimBrowseItem
    ) -> Unit = { _, _ -> },
    onInfoMenuDismissed: () -> Unit = {},
    onShowSavePlaylistNameInput: () -> Unit = {},
    onSavePlaylistNameEntered: (String) -> Unit = {},
    onSavePlaylistDismissed: () -> Unit = {},
    onClearPlaylist: () -> Unit = {},
    onShowVolumePopup: () -> Unit = {}
) {
    val statusState = stateFlow.collectAsStateWithLifecycle()
    val infoMenu by infoMenuFlow.collectAsStateWithLifecycle()
    val playlistSaveInputState by playlistSaveInputStateFlow.collectAsStateWithLifecycle()
    val uiState = statusState.value ?: return
    val scope = rememberCoroutineScope()
    val geometry = rememberPlayerGeometry(
        input = layoutInput,
        insets = insets,
        hasArtist = uiState.artist != null,
        hasAlbum = uiState.album != null
    )

    (infoMenu as? NowPlayingContextMenuState.Show)?.let { menuState ->
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded)
        ModalBottomSheet(
            onDismissRequest = onInfoMenuDismissed,
            sheetState = sheetState
        ) {
            val viewModel = koinViewModel<ContextMenuBottomSheetViewModel> {
                parametersOf(playerId, menuState.items, menuState.parentItem)
            }

            ContextMenuBottomSheetContent(
                viewModel = viewModel,
                serverConfig = serverConfig,
                itemSelectionListener = { item ->
                    onInfoMenuItemSelected(menuState.parentItem, item)
                }
            )
        }
    }

    if (playlistSaveInputState != PlaylistSaveEditorState.Idle) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

        ModalBottomSheet(
            onDismissRequest = onSavePlaylistDismissed,
            sheetState = sheetState
        ) {
            val busy = playlistSaveInputState == PlaylistSaveEditorState.Saving
            BottomSheetContentWrapper(
                title = stringResource(R.string.playlist_save_title),
                busy = busy
            ) {
                InputSheetContent(
                    busy = busy,
                    minLength = 1,
                    onSubmit = onSavePlaylistNameEntered
                )
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        PlayerSurface(
            state = uiState,
            serverConfig = serverConfig,
            geometry = geometry,
            progress = motionState.nowPlaying,
            playlistExpanded = motionState.playlist.isExpanded,
            modifier = Modifier
                .bounds(motionState.nowPlaying, geometry.content.bounds)
                .dragHandle(
                    orientation = geometry.content.dragDirection,
                    progress = motionState.nowPlaying,
                    canClickToClose = false,
                    dragDistance = geometry.content.dragDistance,
                    scope = scope
                ),
            onNavigateUp = {
                scope.launch {
                    motionState.nowPlaying.collapse()
                }
            },
            onPrevious = onPreviousTrack,
            onNext = onNextTrack,
            onToggleRepeat = onToggleRepeat,
            onToggleShuffle = onToggleShuffle,
            onSetPlayingOrPaused = onSetPlayingOrPaused,
            onStop = onStop,
            onSeek = onSeek,
            onShowInfoMenu = onShowInfoMenu,
            onShowSavePlaylistNameInput = onShowSavePlaylistNameInput,
            onClearPlaylist = onClearPlaylist,
            onShowVolumePopup = onShowVolumePopup
        )

        val playlistBackState = remember { PlaylistBackEffectState() }

        PlaylistSheet(
            playlistContent = playlistContent,
            nowPlayingProgress = motionState.nowPlaying,
            playlistProgress = motionState.playlist,
            layoutSpec = geometry.playlist,
            backState = playlistBackState
        )

        MotionProgressBackHandler(
            progress = motionState.nowPlaying,
            enabled = motionState.playlist.isCollapsed || !geometry.playlist.needsHandle,
            easing = { 0.2F * EaseOutQuart.transform(it) }
        )

        PlaylistSheetBackHandler(
            progress = motionState.playlist,
            backState = playlistBackState,
            enabled = geometry.playlist.needsHandle
        )
    }
}

@Composable
private fun PlayerSurface(
    state: NowPlayingUiState,
    serverConfig: ServerConfiguration?,
    geometry: PlayerGeometry,
    progress: MotionProgress,
    playlistExpanded: Boolean,
    modifier: Modifier,
    onNavigateUp: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetPlayingOrPaused: (play: Boolean) -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
    onShowInfoMenu: (NowPlayingContextMenuData) -> Unit,
    onShowSavePlaylistNameInput: () -> Unit,
    onClearPlaylist: () -> Unit,
    onShowVolumePopup: () -> Unit
) {
    Box(
        modifier = modifier
    ) {
        PlayerBackground(
            layoutSpec = geometry.content,
            progress = progress
        )

        PlayerToolbar(
            layoutSpec = geometry.toolbar,
            progress = progress,
            state = state,
            showNowPlayingActions = !playlistExpanded || !geometry.playlist.needsHandle,
            showPlaylistActions = playlistExpanded || !geometry.playlist.needsHandle,
            onNavigateUp = onNavigateUp,
            onShowInfoMenu = onShowInfoMenu,
            onShowSavePlaylistNameInput = onShowSavePlaylistNameInput,
            onClearPlaylist = onClearPlaylist,
            onShowVolumePopup = onShowVolumePopup
        )

        PlayerArtwork(
            state = state,
            serverConfig = serverConfig,
            layoutSpec = geometry.artwork,
            progress = progress
        )

        PlayerInformation(
            state = state,
            geometry = geometry,
            progress = progress
        )

        PlaybackControls(
            state = state,
            layoutSpec = geometry.controls,
            progress = progress,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleRepeat = onToggleRepeat,
            onToggleShuffle = onToggleShuffle,
            onSetPlayingOrPaused = onSetPlayingOrPaused,
            onStop = onStop
        )

        ProgressControls(
            state = state,
            layoutSpec = geometry.progress,
            progress = progress,
            onSeek = onSeek
        )
    }
}

@Composable
private fun PlayerBackground(layoutSpec: ContentLayoutSpec, progress: MotionProgress) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress.value
                val c = layoutSpec.corners
                val shape = RoundedCornerShape(
                    topStart = c.topStart.at(p).toPx(),
                    topEnd = c.topEnd.at(p).toPx(),
                    bottomEnd = c.bottomEnd.at(p).toPx(),
                    bottomStart = c.bottomStart.at(p).toPx()
                )
                drawOutline(shape.createOutline(size, layoutDirection, this), color)
            }
    )
}

@Composable
private fun PlayerToolbar(
    layoutSpec: ControlLayoutSpec,
    state: NowPlayingUiState,
    progress: MotionProgress,
    showNowPlayingActions: Boolean,
    showPlaylistActions: Boolean,
    onNavigateUp: () -> Unit,
    onShowInfoMenu: (NowPlayingContextMenuData) -> Unit,
    onShowSavePlaylistNameInput: () -> Unit,
    onClearPlaylist: () -> Unit,
    onShowVolumePopup: () -> Unit
) {
    Box(
        modifier = Modifier
            .bounds(progress, layoutSpec.bounds)
            .fade(progress, layoutSpec.alpha)
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.now_playing))
            },
            subtitle = {
                Text(state.toolbarSubtitle.resolve())
            },
            navigationIcon = {
                ResourceIconButton(
                    drawableResId = R.drawable.ic_chevron_down_24dp,
                    contentDescriptionResId = R.string.content_description_nowplaying_close,
                    onClick = onNavigateUp
                )
            },
            actions = {
                if (showNowPlayingActions) {
                    state.contextMenuData?.let { data ->
                        ResourceIconButton(
                            drawableResId = R.drawable.ic_info_24dp,
                            contentDescriptionResId = R.string.content_description_context_menu,
                            onClick = { onShowInfoMenu(data) }
                        )
                    }

                    ResourceIconButton(
                        drawableResId = R.drawable.ic_volume_24dp,
                        contentDescriptionResId = R.string.content_description_nowplaying_volume,
                        onClick = onShowVolumePopup
                    )
                }
                if (showPlaylistActions) {
                    var showMenu by remember { mutableStateOf(false) }

                    ResourceIconButton(
                        drawableResId = R.drawable.ic_menu_overflow,
                        contentDescriptionResId =
                            R.string.content_description_nowplaying_playlist_menu,
                        onClick = { showMenu = true }
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_playlist_save)) },
                            onClick = {
                                onShowSavePlaylistNameInput()
                                showMenu = false
                            },
                            leadingIcon = {
                                ResourceIcon(
                                    drawableResId = R.drawable.ic_playlist_save_24dp,
                                    contentDescriptionResId =
                                        R.string.content_description_nowplaying_playlist_save
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_playlist_clear)) },
                            onClick = {
                                onClearPlaylist()
                                showMenu = false
                            },
                            leadingIcon = {
                                ResourceIcon(
                                    drawableResId = R.drawable.ic_playlist_clear_24dp,
                                    contentDescriptionResId =
                                        R.string.content_description_nowplaying_playlist_clear
                                )
                            }
                        )
                    }
                }
            },
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun PlayerArtwork(
    state: NowPlayingUiState,
    serverConfig: ServerConfiguration?,
    layoutSpec: ArtworkLayoutSpec,
    progress: MotionProgress
) = ArtworkImage(
    artwork = state.artwork,
    serverConfig = serverConfig,
    fallbackDrawableResId = R.drawable.ic_album_placeholder,
    enforceLoadingOriginalImageSize = true,
    cornerPercent = {
        layoutSpec.cornerPercentage.at(progress.value).roundToInt()
    },
    modifier = Modifier.bounds(progress, layoutSpec.bounds)
)

@Composable
private fun PlayerInformation(
    state: NowPlayingUiState,
    geometry: PlayerGeometry,
    progress: MotionProgress
) {
    SongInfoText(
        text = state.title.resolve(),
        layoutSpec = geometry.title,
        progress = progress,
        alignment = geometry.textAlignmentProgress,
        fontWeight = FontWeight.Bold
    )

    state.artist?.let { artist ->
        SongInfoText(
            text = artist,
            layoutSpec = geometry.artist,
            progress = progress,
            alignment = geometry.textAlignmentProgress
        )
    }

    state.album?.let { album ->
        SongInfoText(
            text = album,
            layoutSpec = geometry.album,
            progress = progress,
            alignment = geometry.textAlignmentProgress
        )
    }
}

@Composable
private fun PlaybackControls(
    state: NowPlayingUiState,
    layoutSpec: ControlsLayoutSpec,
    progress: MotionProgress,
    onSetPlayingOrPaused: (play: Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onStop: () -> Unit
) {
    PlaybackButton(
        iconResId = state.repeatIconResId,
        contentDescriptionResId = state.repeatContentDescriptionResId,
        layoutSpec = layoutSpec.repeat,
        progress = progress,
        enabled = state.isPowered,
        onClick = onToggleRepeat
    )

    PlaybackButton(
        iconResId = R.drawable.ic_prev_24dp,
        contentDescriptionResId = R.string.content_description_nowplaying_prev,
        layoutSpec = layoutSpec.prevTrack,
        progress = progress,
        enabled = state.playlistPosition > 1,
        onClick = onPrevious
    )

    val playbackStopContentDescription =
        stringResource(R.string.content_description_nowplaying_stop)

    FilledIconToggleButton(
        checked = state.isPlaying,
        onCheckedChange = onSetPlayingOrPaused,
        enabled = state.playPauseEnabled,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier
            .bounds(progress, layoutSpec.playPause.bounds)
            .fade(progress, layoutSpec.playPause.alpha)
            .then(
                if (state.playPauseEnabled) {
                    Modifier
                        .onLongPressConsumeTap(onStop)
                        .semantics {
                            onLongClick(label = playbackStopContentDescription) {
                                onStop()
                                true
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        ResourceIcon(
            drawableResId = state.playPauseIconResId,
            contentDescriptionResId = state.playPauseContentDescriptionResId
        )
    }

    PlaybackButton(
        iconResId = R.drawable.ic_next_24dp,
        contentDescriptionResId = R.string.content_description_nowplaying_next,
        layoutSpec = layoutSpec.nextTrack,
        progress = progress,
        enabled = state.playlistPosition < state.playlistLength,
        onClick = onNext
    )

    PlaybackButton(
        iconResId = state.shuffleIconResId,
        contentDescriptionResId = state.shuffleContentDescriptionResId,
        layoutSpec = layoutSpec.shuffle,
        progress = progress,
        enabled = state.isPowered,
        onClick = onToggleShuffle
    )
}

@Composable
private fun PlaybackButton(
    iconResId: Int,
    contentDescriptionResId: Int,
    layoutSpec: ControlLayoutSpec,
    progress: MotionProgress,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = ResourceIconButton(
    drawableResId = iconResId,
    contentDescriptionResId = contentDescriptionResId,
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
        .bounds(progress, layoutSpec.bounds)
        .fade(progress, layoutSpec.alpha)
)

@Composable
private fun ProgressControls(
    state: NowPlayingUiState,
    layoutSpec: ProgressLayoutSpec,
    progress: MotionProgress,
    onSeek: (Int) -> Unit
) {
    val sliderState = rememberSliderState(
        value = state.positionSeconds,
        trackRange = 0F..state.durationSeconds
    )

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            sliderState.value = state.positionSeconds
            delay(1.seconds)
        }
    }

    val fullVisible by layoutSpec.full.alpha.rememberIsVisible(progress)
    val miniVisible by layoutSpec.mini.alpha.rememberIsVisible(progress)

    if (fullVisible) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .bounds(progress, layoutSpec.full.bounds)
                .fade(progress, layoutSpec.full.alpha)
        ) {
            Text(
                text = DateUtils.formatElapsedTime(sliderState.value.roundToLong()),
                modifier = Modifier.padding(16.dp)
            )

            Slider(
                state = sliderState,
                enabled = state.canSeek,
                onValueChange = {
                    sliderState.value = it
                },
                onValueChangeFinished = {
                    onSeek(sliderState.value.roundToInt())
                },
                modifier = Modifier.weight(1f)
            )

            Text(
                text = DateUtils.formatElapsedTime(state.durationSeconds.roundToLong()),
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (miniVisible) {
        LinearProgressIndicator(
            progress = { sliderState.value / state.durationSeconds },
            modifier = Modifier
                .bounds(progress, layoutSpec.mini.bounds)
                .padding(horizontal = 4.dp)
                .fade(progress, layoutSpec.mini.alpha)
        )
    }
}

@Composable
private fun PlaylistSheet(
    layoutSpec: PlaylistLayoutSpec,
    nowPlayingProgress: MotionProgress,
    playlistProgress: MotionProgress,
    playlistContent: @Composable (Color, Modifier) -> Unit,
    backState: PlaylistBackEffectState
) {
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = Modifier
            .bounds(nowPlayingProgress, layoutSpec.bounds)
            .fade(nowPlayingProgress, layoutSpec.alpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val closedOffset = if (layoutSpec.needsHandle) {
                        placeable.height - layoutSpec.handleHeight.roundToPx()
                    } else {
                        0
                    }
                    layout(placeable.width, placeable.height) {
                        placeable.place(
                            x = 0,
                            y = (closedOffset * (1f - playlistProgress.value)).roundToInt()
                        )
                    }
                }
                .graphicsLayer {
                    val corner = layoutSpec.cornerSize.at(playlistProgress.value).toPx()
                    scaleX = 1F - 0.1F * backState.progress
                    scaleY = 1F - 0.05F * backState.progress
                    transformOrigin = TransformOrigin(0.5F, 1F)
                    shape = RoundedCornerShape(
                        topStart = corner,
                        topEnd = corner,
                        bottomEnd = 0F,
                        bottomStart = 0F
                    )
                    clip = true
                }
                .background(sheetColor)
        ) {
            val topPadding = PaddingValues(
                top = layoutSpec.padding.calculateTopPadding()
            )

            if (layoutSpec.needsHandle) {
                PlaylistHandle(
                    progress = playlistProgress,
                    dragDistance = layoutSpec.handleDragDistance,
                    modifier = Modifier.padding(topPadding)
                )
            } else {
                PlaylistTitle(
                    modifier = Modifier
                        .padding(topPadding)
                        .padding(bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
            playlistContent(
                sheetColor,
                Modifier
                    .padding(layoutSpec.padding)
                    .fade(playlistProgress, layoutSpec.contentAlpha)
                    .fillMaxWidth()
                    .weight(1F)
            )
        }
    }
}

@Stable
internal class PlaylistBackEffectState {
    var progress by mutableFloatStateOf(0f)
        internal set

    suspend fun reset() {
        animate(progress, 0f) { v, _ -> progress = v }
    }
}

@Composable
internal fun PlaylistSheetBackHandler(
    progress: MotionProgress,
    backState: PlaylistBackEffectState,
    enabled: Boolean = true
) = PredictiveBackHandler(enabled && progress.isExpanded) { progressFlow ->
    try {
        progressFlow.collect { backEvent ->
            backState.progress = EaseOutQuart.transform(backEvent.progress)
        }
        coroutineScope {
            launch { backState.reset() }
            launch { progress.collapse() }
        }
    } catch (e: CancellationException) {
        backState.reset()
        throw e
    }
}

@Composable
private fun PlaylistHandle(
    progress: MotionProgress,
    dragDistance: Float,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .dragHandle(
                orientation = Orientation.Vertical,
                progress = progress,
                canClickToClose = true,
                dragDistance = dragDistance,
                scope = scope
            )
    ) {
        Surface(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Box(Modifier.size(width = 32.dp, height = 4.dp))
        }

        PlaylistTitle()
    }
}

@Composable
private fun PlaylistTitle(modifier: Modifier = Modifier) = Text(
    text = stringResource(R.string.playlist),
    style = MaterialTheme.typography.titleSmall,
    modifier = modifier
)

@Composable
private fun SongInfoText(
    text: String,
    layoutSpec: TextLayoutSpec,
    progress: MotionProgress,
    alignment: Morph<Float>, // 0 -> start, 0.5 -> center, 1 -> end
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    val fontSize = layoutSpec.fontSize.at(progress.value)
    val visible by layoutSpec.alpha.rememberIsVisible(progress)

    if (visible) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            fontSize = fontSize,
            fontWeight = fontWeight,
            style = TextStyle(
                lineHeight = fontSize,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = modifier
                .bounds(progress, layoutSpec.bounds)
                .fade(progress, layoutSpec.alpha)
                .alignHorizontally(progress, alignment)
        )
    }
}

@Composable
internal fun Modifier.bounds(progress: MotionProgress, bounds: Morph<Bounds>) =
    this.layout { measurable, _ ->
        val b = bounds.at(progress.value)
        val placeable = measurable.measure(
            Constraints.fixed(b.width.roundToPx(), b.height.roundToPx())
        )
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(b.start.roundToPx(), b.top.roundToPx())
        }
    }

@Composable
internal fun Modifier.fade(progress: MotionProgress, alpha: Morph<Float>) = this.graphicsLayer {
    this.alpha = alpha.at(progress.value)
}

internal fun Modifier.alignHorizontally(progress: MotionProgress, alignment: Morph<Float>) =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val free = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
            placeable.placeRelative((free * alignment.at(progress.value)).roundToInt(), 0)
        }
    }

@Composable
internal fun Modifier.dragHandle(
    orientation: Orientation,
    progress: MotionProgress,
    canClickToClose: Boolean,
    dragDistance: Float,
    scope: CoroutineScope
): Modifier {
    LaunchedEffect(progress, dragDistance) {
        progress.updateDragDistance(dragDistance)
    }

    return this
        .clickable(
            onClick = {
                scope.launch {
                    when {
                        progress.value == 0F -> progress.expand()
                        canClickToClose -> progress.collapse()
                        else -> {}
                    }
                }
            },
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        )
        .anchoredDraggable(
            state = progress.anchoredState(),
            orientation = orientation
        )
}

@Composable
private fun Modifier.onLongPressConsumeTap(onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val timeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                // returns on up, or null on cancel (consumed by a parent drag, left the bounds)
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                false
            } ?: true

            if (timeout) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongPress()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.fastForEach { it.consume() }
                } while (event.changes.fastAny { it.pressed })
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
fun NowPlayingPreview() {
    val status = NowPlayingUiState(
        title = "Some title".toTextResource(),
        artist = "Artist",
        album = "Album",
        artwork = null,
        playbackStartTimestamp = Clock.System.now(),
        playPosition = 35F,
        durationSeconds = 290F,
        isPlaying = true,
        isPowered = true,
        playPauseEnabled = true,
        repeatIconResId = R.drawable.ic_repeat_off_24dp,
        repeatContentDescriptionResId = R.string.content_description_nowplaying_repeat_off,
        shuffleIconResId = R.drawable.ic_shuffle_off_24dp,
        shuffleContentDescriptionResId = R.string.content_description_nowplaying_shuffle_off,
        canSeek = true,
        playlistPosition = 8,
        playlistLength = 15,
        playerName = "Some player",
        contextMenuData = null
    )

    val playlistItemData = (0..4)
        .map { index ->
            Playlist.PlaylistItem(
                title = "Song title $index",
                artist = "Artist $index",
                album = "Album $index",
                actions = JiveActions.EMPTY
            )
        }
        .let { PagingData.from(it) }
    val playlistUiState = PlaylistViewModel.UiState(3, null)

    val motionState = rememberNowPlayingMotionState(initiallyExpanded = false)

    BoxWithConstraints {
        Scaffold { innerPadding ->
            val layoutInput = PlayerGeometryInput.Portrait(
                containerSize = DpSize(maxWidth, maxHeight),
                collapsedHeight = 64.dp,
                cornerSize = 16.dp
            )

            NowPlayingScreen(
                playerId = PlayerId("11:22:33:44:55:66"),
                layoutInput = layoutInput,
                stateFlow = MutableStateFlow(status),
                infoMenuFlow = MutableStateFlow(NowPlayingContextMenuState.Idle),
                playlistSaveInputStateFlow = MutableStateFlow(PlaylistSaveEditorState.Idle),
                motionState = motionState,
                serverConfig = null,
                insets = innerPadding,
                playlistContent = { surfaceColor, modifier ->
                    PlaylistItemColumn(
                        itemsFlow = MutableStateFlow(playlistItemData),
                        uiStateFlow = MutableStateFlow(playlistUiState),
                        scrollRequestFlow = flow { },
                        surfaceColor = surfaceColor,
                        serverConfig = null,
                        modifier = modifier
                    )
                }
            )
        }
    }
}
