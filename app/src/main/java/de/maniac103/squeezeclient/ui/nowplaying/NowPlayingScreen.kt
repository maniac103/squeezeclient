package de.maniac103.squeezeclient.ui.nowplaying

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import coil3.request.fallback
import coil3.size.Size
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
import de.maniac103.squeezeclient.ui.composables.TextResource
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetContent
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    motionState: NowPlayingMotionState,
    serverConfig: ServerConfiguration?,
    insets: PaddingValues,
    onShowVolumePopup: () -> Unit,
    onInfoMenuItemGoAction: (
        action: JiveAction,
        parentItem: SlimBrowseItemList.SlimBrowseItem,
        contextItem: SlimBrowseItemList.SlimBrowseItem
    ) -> Job?,
    onContentBoundsChanged: (DpRect) -> Unit,
    modifier: Modifier = Modifier,
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
        onsetPlayingOrPaused = viewModel::setPlayPauseState,
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
        onContentBoundsChanged = onContentBoundsChanged,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingScreen(
    playerId: PlayerId,
    stateFlow: StateFlow<NowPlayingUiState?>,
    infoMenuFlow: StateFlow<NowPlayingContextMenuState>,
    playlistSaveInputStateFlow: StateFlow<PlaylistSaveEditorState>,
    serverConfig: ServerConfiguration?,
    insets: PaddingValues,
    motionState: NowPlayingMotionState,
    playlistContent: @Composable (Color, Modifier) -> Unit,
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onsetPlayingOrPaused: (play: Boolean) -> Unit = {},
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
    onShowVolumePopup: () -> Unit = {},
    onContentBoundsChanged: (DpRect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val statusState = stateFlow.collectAsStateWithLifecycle()
    val infoMenu by infoMenuFlow.collectAsStateWithLifecycle()
    val playlistSaveInputState by playlistSaveInputStateFlow.collectAsStateWithLifecycle()
    val uiState = statusState.value ?: return
    val scope = rememberCoroutineScope()

    (infoMenu as? NowPlayingContextMenuState.Show)?.let { menuState ->
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        ModalBottomSheet(
            onDismissRequest = onInfoMenuDismissed,
            sheetState = sheetState
        ) {
            ContextMenuBottomSheetContent(
                playerId = playerId,
                initialItems = menuState.items,
                parentItem = menuState.parentItem,
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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val geometry = calculatePlayerGeometry(
            width = maxWidth,
            height = maxHeight,
            insets = insets,
            hasArtist = uiState.artist != null,
            hasAlbum = uiState.album != null,
            progress = motionState.nowPlaying.value
        )

        LaunchedEffect(geometry.content.bounds) {
            val bounds = geometry.content.bounds
            onContentBoundsChanged(DpRect(bounds.left, bounds.top, bounds.right, bounds.bottom))
        }

        PlayerSurface(
            state = uiState,
            serverConfig = serverConfig,
            geometry = geometry,
            playlistExpanded = motionState.playlist.isExpanded,
            modifier = Modifier
                .bounds(geometry.content.bounds)
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
            onTogglePlayPause = { onsetPlayingOrPaused(!uiState.isPlaying) },
            onStop = onStop,
            onSeek = onSeek,
            onShowInfoMenu = onShowInfoMenu,
            onShowSavePlaylistNameInput = onShowSavePlaylistNameInput,
            onClearPlaylist = onClearPlaylist,
            onShowVolumePopup = onShowVolumePopup
        )

        PlaylistSheet(
            playlistContent = playlistContent,
            progress = motionState.playlist,
            layoutSpec = geometry.playlist
        )

        MotionProgressBackHandler(
            progress = motionState.nowPlaying,
            enabled = motionState.playlist.value == 0F || !geometry.playlist.needsHandle,
            progressScaler = { progress -> 0.2F * progress }
        )
        MotionProgressBackHandler(
            progress = motionState.playlist,
            enabled = geometry.playlist.needsHandle
        )
    }
}

@Composable
private fun PlayerSurface(
    state: NowPlayingUiState,
    serverConfig: ServerConfiguration?,
    geometry: PlayerGeometry,
    playlistExpanded: Boolean,
    modifier: Modifier,
    onNavigateUp: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onTogglePlayPause: () -> Unit,
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
            layoutSpec = geometry.content
        )

        PlayerToolbar(
            layoutSpec = geometry.toolbar,
            state = state,
            showNowPlayingActions = !playlistExpanded || !geometry.playlist.needsHandle,
            showPlaylistActions = playlistExpanded,
            onNavigateUp = onNavigateUp,
            onShowInfoMenu = onShowInfoMenu,
            onShowSavePlaylistNameInput = onShowSavePlaylistNameInput,
            onClearPlaylist = onClearPlaylist,
            onShowVolumePopup = onShowVolumePopup
        )

        PlayerArtwork(
            state = state,
            serverConfig = serverConfig,
            bounds = geometry.artwork
        )

        PlayerInformation(
            state = state,
            geometry = geometry
        )

        PlaybackControls(
            state = state,
            geometry = geometry,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleRepeat = onToggleRepeat,
            onToggleShuffle = onToggleShuffle,
            onTogglePlayPause = onTogglePlayPause,
            onStop = onStop
        )

        ProgressControls(
            state = state,
            layoutSpec = geometry.progress,
            onSeek = onSeek
        )
    }
}

@Composable
private fun PlayerBackground(
    layoutSpec: ContentLayoutSpec
) = Box(
    modifier = Modifier
        .fillMaxSize()
        .clip(
            RoundedCornerShape(
                topStart = layoutSpec.corners.topStart,
                topEnd = layoutSpec.corners.topEnd,
                bottomStart = layoutSpec.corners.bottomStart,
                bottomEnd = layoutSpec.corners.bottomEnd
            )
        )
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
)

@Composable
private fun PlayerToolbar(
    layoutSpec: ControlLayoutSpec,
    state: NowPlayingUiState,
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
            .bounds(layoutSpec.bounds)
            .alpha(layoutSpec.alpha)
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.now_playing))
            },
            subtitle = {
                Text(state.toolbarSubtitle.resolve())
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateUp
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_chevron_down_24dp),
                        contentDescription = null // FIXME
                    )
                }
            },
            actions = {
                if (showNowPlayingActions) {
                    state.contextMenuData?.let { data ->
                        IconButton(
                            onClick = {
                                onShowInfoMenu(data)
                            }
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_info_24dp),
                                contentDescription = null // FIXME
                            )
                        }
                    }
                    IconButton(
                        onClick = onShowVolumePopup
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_volume_24dp),
                            contentDescription = null // FIXME
                        )
                    }
                }
                if (showPlaylistActions) {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_menu_overflow),
                            contentDescription = null // FIXME
                        )
                    }
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
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_save_24dp),
                                    contentDescription = null // FIXME
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
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_clear_24dp),
                                    contentDescription = null // FIXME
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
    bounds: Bounds,
) = ArtworkImage(
    artwork = state.artwork,
    serverConfig = serverConfig,
    imageRequestModifier = {
        fallback(R.drawable.ic_album_placeholder)
        size(Size.ORIGINAL)
    },
    modifier = Modifier.bounds(bounds)
)

@Composable
private fun PlayerInformation(
    state: NowPlayingUiState,
    geometry: PlayerGeometry
) {
    SongInfoText(
        text = state.title.resolve(),
        layoutSpec = geometry.title,
        alignmentProgress = geometry.textAlignmentProgress,
        fontWeight = FontWeight.Bold
    )

    state.artist?.let { artist ->
        SongInfoText(
            text = artist,
            layoutSpec = geometry.artist,
            alignmentProgress = geometry.textAlignmentProgress
        )
    }

    state.album?.let { album ->
        SongInfoText(
            text = album,
            layoutSpec = geometry.album,
            alignmentProgress = geometry.textAlignmentProgress
        )
    }
}

@Composable
private fun PlaybackControls(
    state: NowPlayingUiState,
    geometry: PlayerGeometry,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .offset {
                IntOffset(
                    (geometry.controlsCenter.x - 120.dp).roundToPx(),
                    (geometry.controlsCenter.y - 24.dp).roundToPx(),
                )
            }
            .width(240.dp),
        horizontalArrangement =
            Arrangement.spacedBy(
                0.dp,
                Alignment.CenterHorizontally,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaybackButton(
            icon = state.repeatIconResId,
            enabled = state.isPowered,
            onClick = onToggleRepeat,
            modifier = Modifier.alpha(alpha = geometry.repeatAlpha)
        )

        PlaybackButton(
            icon = R.drawable.ic_prev_24dp,
            enabled = state.playlistPosition > 1,
            onClick = onPrevious
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            IconButton(
                onClick = { },
                enabled = state.playPauseEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .combinedClickable(
                        onClick = onTogglePlayPause,
                        onLongClick = onStop
                    )
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(state.playPauseIconResId),
                    contentDescription = null,
                )
            }
        }

        PlaybackButton(
            icon = R.drawable.ic_next_24dp,
            enabled = state.playlistPosition < state.playlistLength,
            onClick = onNext
        )

        PlaybackButton(
            icon = state.shuffleIconResId,
            enabled = state.isPowered,
            onClick = onToggleShuffle,
            modifier = Modifier.alpha(alpha = geometry.shuffleAlpha)
        )
    }
}

@Composable
private fun PlaybackButton(
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
        )
    }
}

@Composable
private fun ProgressControls(
    state: NowPlayingUiState,
    layoutSpec: ProgressLayoutSpec,
    onSeek: (Int) -> Unit,
) {
    val sliderState = rememberSliderState(
        value = state.positionSeconds,
        trackRange = 0F..state.durationSeconds
    )

    LaunchedEffect(state.isPlaying, state.positionSeconds) {
        while (state.isPlaying) {
            sliderState.value = state.positionSeconds
            delay(1.seconds)
        }
    }

    layoutSpec.full
        .takeIf { it.alpha > 0.01F }
        ?.let { spec ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .bounds(spec.bounds)
                    .alpha(spec.alpha)
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

    layoutSpec.mini
        .takeIf { it.alpha > 0.01F }
        ?.let { spec ->
            LinearProgressIndicator(
                progress = { sliderState.value / state.durationSeconds },
                modifier = Modifier
                    .bounds(spec.bounds)
                    .padding(horizontal = 4.dp)
                    .alpha(spec.alpha)
            )
        }
}

@Composable
private fun PlaylistSheet(
    layoutSpec: PlaylistLayoutSpec,
    progress: MotionProgress,
    playlistContent: @Composable (Color, Modifier) -> Unit
) = Box(
    modifier = Modifier
        .bounds(layoutSpec.bounds)
        .alpha(layoutSpec.alpha)
) {
    val sheetHeight = layoutSpec.bounds.height
    val handleHeight = layoutSpec.peekHeight
        .takeIf { it.isSpecified }
    val dragDistanceDp = handleHeight?.let { sheetHeight - it }
    val progressOffsetDp = dragDistanceDp
        ?.let { it * (1F - progress.value) }
        ?: 0.dp

    val dragDistance = LocalDensity.current.run { dragDistanceDp?.toPx() }
    val cornerSize = lerp(16.dp, 0.dp, progress.value)
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, progressOffsetDp.roundToPx()) }
            .clip(
                RoundedCornerShape(
                    topStart = cornerSize,
                    topEnd = cornerSize
                )
            )
            .background(sheetColor)
    ) {
        // FIXME: even if not draggable header should be present
        if (layoutSpec.needsHandle) {
            PlaylistHandle(
                progress = progress,
                dragDistance = dragDistance ?: 0F
            )
        }
        val contentAlpha = if (layoutSpec.needsHandle) {
            lerp(0F, 1F, progress.value)
        } else {
            1F
        }
        playlistContent(
            sheetColor,
            Modifier
                .padding(layoutSpec.padding)
                .alpha(contentAlpha)
                .fillMaxWidth()
                .weight(1F)
        )
    }
}

@Composable
private fun PlaylistHandle(
    progress: MotionProgress,
    dragDistance: Float
) {
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Box(Modifier.size(width = 32.dp, height = 4.dp))
        }

        Text(stringResource(R.string.playlist))
    }
}

@Composable
private fun SongInfoText(
    text: String,
    layoutSpec: TextLayoutSpec,
    alignmentProgress: Float, // 0 -> start, 0.5 -> center, 1 -> end
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    var textWidth by remember(text) {
        mutableIntStateOf(0)
    }

    BoxWithConstraints(
        modifier = modifier
            .bounds(layoutSpec.bounds)
            .alpha(layoutSpec.alpha)
    ) {
        val remainingHorizontalSpace = (constraints.maxWidth - textWidth)
            .coerceAtLeast(0)

        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            fontSize = layoutSpec.fontSize,
            fontWeight = fontWeight,
            softWrap = false,
            style = TextStyle(
                lineHeight = layoutSpec.fontSize,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            onTextLayout = { result ->
                textWidth =
                    result.multiParagraph
                        .getLineRight(0)
                        .roundToInt()
            },
            modifier = Modifier.offset {
                IntOffset(
                    x = (remainingHorizontalSpace * alignmentProgress).roundToInt(),
                    y = 0,
                )
            }
        )
    }
}

@Composable
internal fun Modifier.bounds(bounds: Bounds) = this
    .offset { IntOffset(bounds.left.roundToPx(), bounds.top.roundToPx()) }
    .size(bounds.width, bounds.height)

@Composable
internal fun Modifier.dragHandle(
    orientation: Orientation,
    progress: MotionProgress,
    canClickToClose: Boolean,
    dragDistance: Float,
    scope: CoroutineScope,
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
            orientation = orientation,
        )
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
fun NowPlayingPreview() {
    val uiState = NowPlayingUiState(
        title = TextResource.Raw("Song title"),
        artist = "Artist",
        album = "Album",
        artwork = null,
        positionSeconds = 67F,
        durationSeconds = 270F,
        isPlaying = true,
        isPowered = true,
        canSeek = true,
        playPauseEnabled = true,
        playPauseIconResId = R.drawable.ic_play_24dp,
        repeatIconResId = R.drawable.ic_repeat_off_24dp,
        shuffleIconResId = R.drawable.ic_shuffle_off_24dp,
        playlistPosition = 8,
        playlistLength = 17,
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

    Scaffold { innerPadding ->
        NowPlayingScreen(
            playerId = PlayerId("11:22:33:44:55:66"),
            stateFlow = MutableStateFlow(uiState),
            infoMenuFlow = MutableStateFlow(NowPlayingContextMenuState.Idle),
            playlistSaveInputStateFlow = MutableStateFlow(PlaylistSaveEditorState.Idle),
            motionState = motionState,
            serverConfig = null,
            insets = innerPadding,
            playlistContent = { surfaceColor, modifier ->
                PlaylistItemColumn(
                    itemsFlow = MutableStateFlow(playlistItemData),
                    uiStateFlow = MutableStateFlow(playlistUiState),
                    scrollRequestFlow = flow {  },
                    surfaceColor = surfaceColor,
                    serverConfig = null,
                    modifier = modifier
                )
            },
            onContentBoundsChanged = {}
        )
    }
}