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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.kmpalette.rememberPaletteState
import com.materialkolor.PaletteStyle
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.loadMaybeRelativeUrl
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItem
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetContent
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.ui.composables.NavigationBarScrim
import de.maniac103.squeezeclient.ui.composables.ResourceIcon
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton
import de.maniac103.squeezeclient.ui.composables.toTextResource
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetContent
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuViewModel
import de.maniac103.squeezeclient.ui.maincontent.MainLayout
import de.maniac103.squeezeclient.ui.theme.AppTheme
import de.maniac103.squeezeclient.ui.theme.AppThemeAccentOverride
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
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
    themeModeState: State<Preferences.AppThemeMode>,
    accentModeState: State<Preferences.NowPlayingAccentMode>,
    insets: PaddingValues,
    onShowVolumePopup: () -> Unit,
    onInfoMenuItemGoAction: (
        action: JiveAction,
        parentItem: SlimBrowseItem,
        contextItem: SlimBrowseItem
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
        themeModeState = themeModeState,
        accentModeState = accentModeState,
        insets = insets,
        playlistContent = {
            Playlist(
                state = listState,
                viewModel = playlistViewModel,
                itemColors = itemColors,
                serverConfig = serverConfig,
                contentPadding = contentPadding,
                modifier = listModifier
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
            job?.apply {
                invokeOnCompletion {
                    viewModel.onContextMenuDismissed()
                    scope.launch {
                        motionState.nowPlaying.collapse()
                    }
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
    themeModeState: State<Preferences.AppThemeMode>,
    accentModeState: State<Preferences.NowPlayingAccentMode>,
    serverConfig: ServerConfiguration?,
    insets: PaddingValues,
    motionState: NowPlayingMotionState,
    playlistContent: @Composable PlaylistSheetScope.() -> Unit,
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
        parentItem: SlimBrowseItem,
        contextItem: SlimBrowseItem
    ) -> Job? = { _, _ -> null },
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
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        ModalBottomSheet(
            onDismissRequest = onInfoMenuDismissed,
            sheetState = sheetState
        ) {
            val viewModel = koinViewModel<ContextMenuViewModel> { parametersOf(playerId) }
            viewModel.setRootPage(menuState.parentItem, menuState.items)

            LaunchedEffect(Unit) {
                sheetState.show()
            }

            ContextMenuBottomSheetContent(
                viewModel = viewModel,
                serverConfig = serverConfig,
                onItemSelected = { item ->
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
            InputBottomSheetContent(
                title = stringResource(R.string.playlist_save_title),
                busy = busy,
                minLength = 1,
                onSubmit = onSavePlaylistNameEntered,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    val artworkUrl = uiState.artwork?.extractIconUrl()
    val context = LocalContext.current
    val paletteState = rememberPaletteState()

    LaunchedEffect(artworkUrl, context, accentModeState.value) {
        val url = artworkUrl
            ?.takeIf { accentModeState.value != Preferences.NowPlayingAccentMode.AppAccent }
            ?: return@LaunchedEffect
        val imageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .loadMaybeRelativeUrl(url, serverConfig)
            .size(128, 128)
            .allowHardware(false)
            .build()

        val result = imageLoader.execute(request)
        if (result is SuccessResult) {
            val imageBitmap = result.image.toBitmap().asImageBitmap()
            paletteState.generate(imageBitmap)
        }
    }

    val accentOverride = when (accentModeState.value) {
        Preferences.NowPlayingAccentMode.Tonal if paletteState.palette != null ->
            AppThemeAccentOverride(paletteState, PaletteStyle.TonalSpot)

        Preferences.NowPlayingAccentMode.Vibrant if paletteState.palette != null ->
            AppThemeAccentOverride(paletteState, PaletteStyle.Vibrant)

        else -> null
    }

    AppTheme(
        mode = themeModeState.value,
        accentOverride = accentOverride
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
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
                        .then(
                            geometry.content.dragDirection?.let { direction ->
                                Modifier.dragHandle(
                                    orientation = direction,
                                    progress = motionState.nowPlaying,
                                    enabled = uiState.canExpand && motionState.playlist.isCollapsed,
                                    canClickToClose = false,
                                    dragDistance = geometry.content.dragDistance,
                                    scope = scope
                                )
                            }
                                ?: Modifier
                        ),
                    onNavigateUp = {
                        scope.launch {
                            motionState.playlist.collapse()
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
            showCloseIcon = geometry.content.dragDirection != null,
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
    showCloseIcon: Boolean,
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
                Text(
                    text = state.toolbarSubtitle.resolve(),
                    textAlign = TextAlign.Center
                )
            },
            navigationIcon = {
                if (showCloseIcon) {
                    ResourceIconButton(
                        drawableResId = R.drawable.ic_chevron_down_24dp,
                        contentDescriptionResId = R.string.content_description_nowplaying_close,
                        onClick = onNavigateUp
                    )
                }
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

                    if (state.hasVolumeControl) {
                        ResourceIconButton(
                            drawableResId = R.drawable.ic_volume_24dp,
                            contentDescriptionResId =
                                R.string.content_description_nowplaying_volume,
                            onClick = onShowVolumePopup
                        )
                    }
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
    PlaybackToggleButton(
        iconResId = state.repeatIconResId,
        contentDescriptionResId = state.repeatContentDescriptionResId,
        checked = state.repeatChecked,
        layoutSpec = layoutSpec.repeat,
        progress = progress,
        enabled = state.playbackControlsEnabled,
        onClick = onToggleRepeat
    )

    PlaybackButton(
        iconResId = R.drawable.ic_prev_24dp,
        contentDescriptionResId = R.string.content_description_nowplaying_prev,
        layoutSpec = layoutSpec.prevTrack,
        progress = progress,
        enabled = state.playbackControlsEnabled && state.playlistPosition > 1,
        onClick = onPrevious
    )

    PlayPauseButton(
        isPlaying = state.isPlaying,
        enabled = state.playbackControlsEnabled && state.playPauseEnabled,
        contentDescriptionResId = state.playPauseContentDescriptionResId,
        layoutSpec = layoutSpec.playPause,
        progress = progress,
        onSetPlayingOrPaused = onSetPlayingOrPaused,
        onStop = onStop
    )

    PlaybackButton(
        iconResId = R.drawable.ic_next_24dp,
        contentDescriptionResId = R.string.content_description_nowplaying_next,
        layoutSpec = layoutSpec.nextTrack,
        progress = progress,
        enabled = state.playbackControlsEnabled && state.playlistPosition < state.playlistLength,
        onClick = onNext
    )

    PlaybackToggleButton(
        iconResId = state.shuffleIconResId,
        contentDescriptionResId = state.shuffleContentDescriptionResId,
        checked = state.shuffleChecked,
        layoutSpec = layoutSpec.shuffle,
        progress = progress,
        enabled = state.playbackControlsEnabled,
        onClick = onToggleShuffle
    )
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    contentDescriptionResId: Int,
    layoutSpec: ControlLayoutSpec,
    progress: MotionProgress,
    onSetPlayingOrPaused: (play: Boolean) -> Unit,
    onStop: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var checked by remember { mutableStateOf(false) }
    val playbackStopContentDescription =
        stringResource(R.string.content_description_nowplaying_stop)

    LaunchedEffect(isPlaying) {
        checked = isPlaying
    }

    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { value ->
            checked = value
            onSetPlayingOrPaused(value)
        },
        enabled = enabled,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier
            .bounds(progress, layoutSpec.bounds)
            .fade(progress, layoutSpec.alpha)
            .onLongPressConsumeTap(onStop)
            .semantics {
                onLongClick(label = playbackStopContentDescription) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStop()
                    true
                }
            }
    ) {
        val icon = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause_anim)
        Icon(
            painter = rememberAnimatedVectorPainter(
                animatedImageVector = icon,
                atEnd = isPlaying
            ),
            contentDescription = stringResource(contentDescriptionResId)
        )
    }
}

@Composable
private fun PlaybackToggleButton(
    iconResId: Int,
    contentDescriptionResId: Int,
    checked: Boolean,
    layoutSpec: ControlLayoutSpec,
    progress: MotionProgress,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = FilledTonalIconToggleButton(
    checked = checked,
    onCheckedChange = { onClick() },
    enabled = enabled,
    shapes = IconButtonDefaults.toggleableShapes(),
    modifier = modifier
        .bounds(progress, layoutSpec.bounds)
        .fade(progress, layoutSpec.alpha)
) {
    ResourceIcon(
        drawableResId = iconResId,
        contentDescriptionResId = contentDescriptionResId
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
) = FilledTonalIconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
        .bounds(progress, layoutSpec.bounds)
        .fade(progress, layoutSpec.alpha)
) {
    ResourceIcon(
        drawableResId = iconResId,
        contentDescriptionResId = contentDescriptionResId
    )
}

@Composable
private fun ProgressControls(
    state: NowPlayingUiState,
    layoutSpec: ProgressLayoutSpec,
    progress: MotionProgress,
    onSeek: (Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var autoAdvanceProgress by remember(state) { mutableStateOf(state.isPlaying) }
    val sliderState = key(state.durationSeconds) {
        rememberSliderState(
            value = state.positionSeconds,
            trackRange = 0F..state.durationSeconds
        )
    }

    LaunchedEffect(state, autoAdvanceProgress) {
        while (autoAdvanceProgress) {
            sliderState.value = state.positionSeconds
            delay(1.seconds)
        }
    }

    val fullVisible by layoutSpec.full.alpha.rememberIsVisible(progress)
    val miniVisible by layoutSpec.mini.alpha.rememberIsVisible(progress)

    if (fullVisible) {
        Box(
            modifier = Modifier
                .bounds(progress, layoutSpec.full.bounds)
                .fade(progress, layoutSpec.full.alpha)
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val interactions = remember { mutableStateListOf<Interaction>() }

            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is FocusInteraction.Focus -> interactions.add(interaction)
                        is FocusInteraction.Unfocus -> interactions.remove(interaction.focus)
                        is PressInteraction.Press -> interactions.add(interaction)
                        is PressInteraction.Release -> interactions.remove(interaction.press)
                        is PressInteraction.Cancel -> interactions.remove(interaction.press)
                        is DragInteraction.Start -> interactions.add(interaction)
                        is DragInteraction.Stop -> interactions.remove(interaction.start)
                        is DragInteraction.Cancel -> interactions.remove(interaction.start)
                    }
                }
            }

            val isInteracting = interactions.isNotEmpty()
            val waveAmplitude = when {
                !state.isPlaying -> 0.dp
                isInteracting -> 2.dp
                else -> 4.dp
            }

            WavySlider(
                sliderState = sliderState,
                enabled = state.canSeek,
                trackHeight = 6.dp,
                thumbSize = 24.dp,
                thumbWidth = if (isInteracting) 8.dp else 24.dp,
                waveAmplitude = waveAmplitude,
                onValueChange = {
                    autoAdvanceProgress = false
                    sliderState.value = it
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                },
                onValueChangeFinished = {
                    val newPosition = sliderState.value.roundToInt()
                    if (newPosition != state.positionSeconds.roundToInt()) {
                        onSeek(sliderState.value.roundToInt())
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    } else {
                        autoAdvanceProgress = true
                    }
                },
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            Text(
                text = DateUtils.formatElapsedTime(sliderState.value.roundToLong()),
                style = MaterialTheme.typography.labelLargeEmphasized,
                modifier = Modifier
                    .align(AbsoluteAlignment.BottomLeft)
                    .padding(horizontal = 8.dp)
            )

            Text(
                text = DateUtils.formatElapsedTime(state.durationSeconds.roundToLong()),
                style = MaterialTheme.typography.labelLargeEmphasized,
                modifier = Modifier
                    .align(AbsoluteAlignment.BottomRight)
                    .padding(horizontal = 8.dp)
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
private fun WavySlider(
    sliderState: SliderState,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumbSize: Dp,
    thumbWidth: Dp,
    trackHeight: Dp,
    waveAmplitude: Dp
) {
    val waveOffset = remember { Animatable(0f) }
    val animatedWaveAmplitude = remember { Animatable(0.dp, Dp.VectorConverter) }
    val waveActive by remember {
        computedStateOf { animatedWaveAmplitude.value > 0.dp }
    }

    LaunchedEffect(waveAmplitude) {
        animatedWaveAmplitude.animateTo(
            targetValue = waveAmplitude,
            animationSpec = tween(500)
        )
    }

    LaunchedEffect(waveActive) {
        if (waveActive) {
            waveOffset.animateTo(
                targetValue = 2 * PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 2000,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            waveOffset.snapTo(0F)
        }
    }

    val animatedThumbWidth = remember { Animatable(thumbSize, Dp.VectorConverter) }
    LaunchedEffect(thumbWidth) {
        animatedThumbWidth.animateTo(
            targetValue = thumbWidth,
            animationSpec = tween(500)
        )
    }

    Slider(
        state = sliderState,
        enabled = enabled,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier,
        track = { state ->
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbSize)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val amplitude = animatedWaveAmplitude.value.toPx()
                val waveLength = canvasWidth / 10
                val activeWidth = canvasWidth * state.coercedValueAsFraction

                val path = Path().apply {
                    for (x in 0..activeWidth.toInt()) {
                        val angle = ((x / waveLength) * 2 * PI) + waveOffset.value
                        val y = (canvasHeight / 2) + amplitude * sin(angle).toFloat()
                        if (x == 0) moveTo(0F, y) else lineTo(x.toFloat(), y)
                    }
                }

                val activeColor = when (enabled) {
                    true -> colors.activeTrackColor
                    false -> colors.disabledActiveTrackColor
                }
                val inactiveColor = when (enabled) {
                    true -> colors.inactiveTrackColor
                    false -> colors.disabledInactiveTrackColor
                }

                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(
                        width = trackHeight.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                drawLine(
                    color = inactiveColor,
                    start = Offset(x = activeWidth, y = canvasHeight / 2),
                    end = Offset(x = canvasWidth, y = canvasHeight / 2),
                    strokeWidth = trackHeight.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = activeColor,
                    radius = trackHeight.toPx() * 0.4F,
                    center = Offset(x = canvasWidth, y = canvasHeight / 2)
                )
            }
        },
        thumb = {
            val color = if (enabled) colors.thumbColor else colors.disabledThumbColor
            Box(
                modifier = Modifier.size(thumbSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(color, shape = RoundedCornerShape(percent = 50))
                        .width(animatedThumbWidth.value)
                        .height(thumbSize)
                )
            }
        }
    )
}

private class PlaylistNestedScrollConnection(
    private val listState: LazyListState,
    private val progress: MotionProgress,
    private val velocityThreshold: Float
) : NestedScrollConnection {
    private var listTakesScroll: Boolean? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) {
            return Offset.Zero
        }

        val delta = available.y
        return if (delta < 0F) {
            // Moving upwards resets the list scroll consumption flag
            listTakesScroll = null
            Offset(0F, progress.consumeScrollDelta(delta))
        } else {
            // Moving downwards: list gets every scroll if it could scroll initially
            if (listTakesScroll == null) {
                listTakesScroll = listState.canScrollBackward
            }
            Offset.Zero
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        if (source != NestedScrollSource.UserInput || listTakesScroll == true) {
            Offset.Zero
        } else {
            Offset(0F, progress.consumeScrollDelta(available.y))
        }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val velocity = available.y
        val forwardToList = listTakesScroll == true
        listTakesScroll = null

        return if (forwardToList) {
            Velocity.Zero
        } else {
            when {
                velocity > velocityThreshold -> progress.collapse()
                velocity < -velocityThreshold -> progress.expand()
                progress.value >= 0.5f -> progress.expand()
                else -> progress.collapse()
            }
            available
        }
    }
}

internal data class PlaylistSheetScope(
    val listState: LazyListState,
    val itemColors: ListItemColors,
    val contentPadding: PaddingValues,
    val listModifier: Modifier
)

@Composable
private fun PlaylistSheet(
    layoutSpec: PlaylistLayoutSpec,
    nowPlayingProgress: MotionProgress,
    playlistProgress: MotionProgress,
    playlistContent: @Composable PlaylistSheetScope.() -> Unit,
    backState: PlaylistBackEffectState
) {
    val playlistState = rememberLazyListState()
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val itemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    val velocityThreshold = LocalDensity.current.run { 125.dp.toPx() }
    val needsHandle = layoutSpec.needsHandle
    val nestedScrollModifier = remember(playlistProgress, velocityThreshold, needsHandle) {
        if (needsHandle) {
            val scrollConnection = PlaylistNestedScrollConnection(
                listState = playlistState,
                progress = playlistProgress,
                velocityThreshold = velocityThreshold
            )
            Modifier.nestedScroll(scrollConnection)
        } else {
            Modifier
        }
    }

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
                    val closedOffset = when (needsHandle) {
                        true -> placeable.height - layoutSpec.handleHeight.roundToPx()
                        false -> 0
                    }
                    layout(placeable.width, placeable.height) {
                        placeable.place(
                            x = 0,
                            y = (closedOffset * (1f - playlistProgress.value)).roundToInt()
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = calculateSheetPredictiveBackScaleX(backState.progress)
                    scaleY = calculateSheetPredictiveBackScaleY(backState.progress)
                    transformOrigin = TransformOrigin(0.5F, 1F)

                    val progress = nowPlayingProgress.value
                    shape = RoundedCornerShape(
                        topStart = layoutSpec.cornerSizes.topStart.at(progress),
                        topEnd = layoutSpec.cornerSizes.topEnd.at(progress),
                        bottomStart = layoutSpec.cornerSizes.bottomStart.at(progress),
                        bottomEnd = layoutSpec.cornerSizes.bottomEnd.at(progress)
                    )
                    clip = true
                }
                .background(sheetColor)
                .then(nestedScrollModifier)
        ) {
            Spacer(
                modifier = Modifier.layout { _, constraints ->
                    val h = layoutSpec.topSpacing.at(nowPlayingProgress.value).roundToPx()
                    layout(constraints.maxWidth, h) {}
                }
            )

            if (needsHandle) {
                PlaylistHandle(
                    progress = playlistProgress,
                    dragDistance = layoutSpec.handleDragDistance
                )
            } else {
                PlaylistTitle(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F)
            ) {
                val sheetScope = PlaylistSheetScope(
                    listState = playlistState,
                    itemColors = itemColors,
                    contentPadding = layoutSpec.contentPadding,
                    listModifier = Modifier
                        .fade(playlistProgress, layoutSpec.contentAlpha)
                        .fillMaxSize()
                        .graphicsLayer {
                            val xScale = calculateSheetPredictiveBackScaleX(backState.progress)
                            val yScale = calculateSheetPredictiveBackScaleY(backState.progress)
                            scaleY = if (yScale != 0f) xScale / yScale else 1F
                            transformOrigin = TransformOrigin(0.5F, 0F)
                        }
                )

                with(sheetScope) {
                    playlistContent()
                }

                NavigationBarScrim(
                    color = sheetColor,
                    insets = layoutSpec.contentPadding
                )
            }
        }
    }
}

internal fun GraphicsLayerScope.calculateSheetPredictiveBackScaleX(progress: Float): Float {
    val width = size.width
    return if (width.isNaN() || width == 0f) {
        1f
    } else {
        1f - lerp(0f, min(48.dp.toPx(), width), progress) / width
    }
}

internal fun GraphicsLayerScope.calculateSheetPredictiveBackScaleY(progress: Float): Float {
    val height = size.height
    return if (height.isNaN() || height == 0f) {
        1f
    } else {
        1f - lerp(0f, min(24.dp.toPx(), height), progress) / height
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
                enabled = true,
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
    this.layout { measurable, constraints ->
        val b = bounds.at(progress.value)
        val placeable = measurable.measure(
            Constraints.fixed(b.width.roundToPx(), b.height.roundToPx())
        )
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
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
    enabled: Boolean,
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
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        )
        .anchoredDraggable(
            state = progress.anchoredState(),
            enabled = enabled,
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

@PreviewLightDark
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
        playPauseEnabled = true,
        playbackControlsEnabled = true,
        repeatIconResId = R.drawable.ic_repeat_24dp,
        repeatContentDescriptionResId = R.string.content_description_nowplaying_repeat_off,
        repeatChecked = true,
        shuffleIconResId = R.drawable.ic_shuffle_24dp,
        shuffleContentDescriptionResId = R.string.content_description_nowplaying_shuffle_off,
        shuffleChecked = false,
        canSeek = true,
        canExpand = true,
        hasVolumeControl = true,
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

    val motionState = rememberNowPlayingMotionState(initiallyExpanded = true)
    val themeMode = remember { mutableStateOf(Preferences.AppThemeMode.SystemDefault) }
    val accentMode = remember { mutableStateOf(Preferences.NowPlayingAccentMode.AppAccent) }

    BoxWithConstraints {
        Scaffold { innerPadding ->
            val layoutInput = PlayerGeometryInput.Portrait(
                containerSize = DpSize(maxWidth, maxHeight),
                collapsedHeight = 64.dp,
                cornerSize = 16.dp,
                playlistMinSidePadding = 8.dp
            )

            NowPlayingScreen(
                playerId = PlayerId("11:22:33:44:55:66"),
                layoutInput = layoutInput,
                stateFlow = MutableStateFlow(status),
                infoMenuFlow = MutableStateFlow(NowPlayingContextMenuState.Idle),
                playlistSaveInputStateFlow = MutableStateFlow(PlaylistSaveEditorState.Idle),
                motionState = motionState,
                serverConfig = null,
                themeModeState = themeMode,
                accentModeState = accentMode,
                insets = innerPadding,
                playlistContent = {
                    Playlist(
                        state = listState,
                        itemsFlow = MutableStateFlow(playlistItemData),
                        uiStateFlow = MutableStateFlow(playlistUiState),
                        scrollRequestFlow = flow { },
                        itemColors = itemColors,
                        serverConfig = null,
                        contentPadding = contentPadding,
                        modifier = listModifier
                    )
                }
            )
        }
    }
}
