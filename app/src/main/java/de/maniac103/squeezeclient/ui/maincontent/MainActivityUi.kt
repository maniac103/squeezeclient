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

package de.maniac103.squeezeclient.ui.maincontent

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.Player
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.ui.ActivePlayerScope
import de.maniac103.squeezeclient.ui.composables.NavigationBarScrim
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton
import de.maniac103.squeezeclient.ui.displaystatus.DisplayStatusIndicator
import de.maniac103.squeezeclient.ui.nowplaying.NowPlayingScreen
import de.maniac103.squeezeclient.ui.nowplaying.rememberNowPlayingMotionState
import de.maniac103.squeezeclient.ui.search.SearchBox
import de.maniac103.squeezeclient.ui.search.SearchViewModel
import de.maniac103.squeezeclient.ui.volume.VolumePopup
import de.maniac103.squeezeclient.ui.volume.VolumePopupViewModel
import kotlin.String
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.scope.KoinScope
import org.koin.core.annotation.KoinExperimentalAPI

sealed interface MainLayout {
    val containerSize: DpSize
    val mainContentPadding: PaddingValues
    val mainContentPagePadding: PaddingValues
    val compensateMainContentBottomPagePaddingDuringAnimation: Boolean
    val breadcrumbsPadding: PaddingValues
    val searchBoxPadding: PaddingValues
    val toolbarPadding: PaddingValues
    val toolbarContentPadding: PaddingValues
    val nowPlayingCornerSize: Dp
    val useGridForSlimBrowseItemsByDefault: Boolean
    val slimBrowseGridCellMinSize: Dp

    data class Portrait(
        override val containerSize: DpSize,
        override val mainContentPadding: PaddingValues,
        override val mainContentPagePadding: PaddingValues,
        override val compensateMainContentBottomPagePaddingDuringAnimation: Boolean = true,
        override val breadcrumbsPadding: PaddingValues,
        override val searchBoxPadding: PaddingValues,
        override val toolbarPadding: PaddingValues,
        override val toolbarContentPadding: PaddingValues,
        val collapsedNowPlayingHeight: Dp,
        override val nowPlayingCornerSize: Dp
    ) : MainLayout {
        override val useGridForSlimBrowseItemsByDefault = false
        override val slimBrowseGridCellMinSize = 120.dp
    }

    data class Landscape(
        override val containerSize: DpSize,
        override val mainContentPadding: PaddingValues,
        override val mainContentPagePadding: PaddingValues,
        override val compensateMainContentBottomPagePaddingDuringAnimation: Boolean = false,
        override val searchBoxPadding: PaddingValues,
        override val breadcrumbsPadding: PaddingValues,
        override val toolbarPadding: PaddingValues,
        override val toolbarContentPadding: PaddingValues,
        val collapsedNowPlayingWidth: Dp,
        override val nowPlayingCornerSize: Dp
    ) : MainLayout {
        override val useGridForSlimBrowseItemsByDefault = true
        override val slimBrowseGridCellMinSize = 160.dp
    }
}

@Composable
fun MainActivityUi(
    volumeChangeRequestFlow: SharedFlow<Int>,
    onGalleryItemSelected: (SlideshowImage) -> Unit,
    onDownloadSelected: (DownloadRequestData) -> Unit,
    onWebLinkSelected: (title: String, link: Uri) -> Unit,
    onOpenServerSetup: () -> Unit,
    onManagePlayers: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel = koinViewModel<MainActivityViewModel>()
    MainActivityUi(
        stateFlow = viewModel.stateFlow,
        forceGridForSlimBrowseItemsFlow = viewModel.forceGridForSlimBrowseItemsFlow,
        localPlaybackEnabledFlow = viewModel.localPlaybackEnabledFlow,
        volumeChangeRequestFlow = volumeChangeRequestFlow,
        onGalleryItemSelected = onGalleryItemSelected,
        onDownloadSelected = onDownloadSelected,
        onWebLinkSelected = onWebLinkSelected,
        onRetryConnection = viewModel::retryConnection,
        onPlayerSelected = viewModel::selectPlayer,
        onOpenServerSetup = onOpenServerSetup,
        onManagePlayers = onManagePlayers,
        onOpenSettings = onOpenSettings,
        onLocalPlaybackEnabledChanged = viewModel::setLocalPlaybackEnabled
    )
}

@Composable
fun MainActivityUi(
    stateFlow: StateFlow<MainActivityState>,
    forceGridForSlimBrowseItemsFlow: Flow<Boolean>,
    localPlaybackEnabledFlow: Flow<Boolean>,
    volumeChangeRequestFlow: SharedFlow<Int>,
    onGalleryItemSelected: (SlideshowImage) -> Unit = {},
    onDownloadSelected: (DownloadRequestData) -> Unit = {},
    onWebLinkSelected: (title: String, link: Uri) -> Unit = { _, _ -> },
    onRetryConnection: () -> Unit = {},
    onOpenServerSetup: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onManagePlayers: () -> Unit = {},
    onPlayerSelected: (PlayerId) -> Unit = {},
    onLocalPlaybackEnabledChanged: (Boolean) -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val state by stateFlow.collectAsStateWithLifecycle()
    val serverConfigState = rememberUpdatedState(state.serverConfig)

    val forceGridForSlimBrowseItemsState =
        forceGridForSlimBrowseItemsFlow.collectAsStateWithLifecycle(false)
    val localPlaybackEnabledState = localPlaybackEnabledFlow.collectAsStateWithLifecycle(false)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainActivityDrawerContent(
                drawerState = drawerState,
                serverConfig = state.serverConfig,
                connectedState = state as? MainActivityState.Connected,
                localPlaybackEnabled = localPlaybackEnabledState.value,
                onOpenServerSetup = onOpenServerSetup,
                onOpenSettings = onOpenSettings,
                onManagePlayers = onManagePlayers,
                onPlayerSelected = onPlayerSelected,
                onLocalPlaybackEnabledChanged = onLocalPlaybackEnabledChanged
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            state.selectedPlayer?.let { player ->
                MainActivityPlayerContent(
                    drawerState = drawerState,
                    selectedPlayer = player,
                    serverConfigState = serverConfigState,
                    visible = state is MainActivityState.Connected,
                    forceGridForSlimBrowseItemsState = forceGridForSlimBrowseItemsState,
                    volumeChangeRequestFlow = volumeChangeRequestFlow,
                    onGalleryItemSelected = onGalleryItemSelected,
                    onDownloadSelected = onDownloadSelected,
                    onWebLinkSelected = onWebLinkSelected
                )
            }

            when (val state = state) {
                is MainActivityState.Loading -> {
                    MainActivityLoadingIndicator(
                        drawerState = drawerState
                    )
                }

                is MainActivityState.CouldNotConnect -> {
                    MainActivityErrorContent(
                        drawerState = drawerState,
                        textResId = R.string.connection_error_text_no_connection,
                        subtext = null,
                        buttons = listOf(
                            ActionButtonSpec(
                                labelResId = R.string.connection_error_action_retry,
                                onClick = onRetryConnection
                            ),
                            ActionButtonSpec(
                                labelResId = R.string.connection_error_action_setup_server,
                                onClick = onOpenServerSetup
                            )
                        )
                    )
                }

                is MainActivityState.ConnectionFailed -> {
                    MainActivityErrorContent(
                        drawerState = drawerState,
                        textResId = R.string.connection_error_text_connection_failure,
                        subtext = state.cause.message,
                        buttons = listOf(
                            ActionButtonSpec(
                                labelResId = R.string.connection_error_action_retry,
                                onClick = onRetryConnection
                            )
                        )
                    )
                }

                is MainActivityState.NoPlayers -> {
                    MainActivityErrorContent(
                        drawerState = drawerState,
                        iconResId = R.drawable.ic_player_24dp,
                        textResId = R.string.connection_error_text_no_player,
                        subtext = null,
                        buttons = emptyList()
                    )
                }

                is MainActivityState.Connected -> {
                    // Already made MainActivityPlayerContent visible above
                }
            }
        }
    }
}

@Composable
private fun MainActivityDrawerContent(
    drawerState: DrawerState,
    serverConfig: ServerConfiguration?,
    connectedState: MainActivityState.Connected?,
    localPlaybackEnabled: Boolean,
    onOpenServerSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onManagePlayers: () -> Unit,
    onPlayerSelected: (PlayerId) -> Unit,
    onLocalPlaybackEnabledChanged: (Boolean) -> Unit
) {
    val drawerInsets = DrawerDefaults.windowInsets
    val headerInsets = drawerInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
    val contentInsets = drawerInsets.only(
        WindowInsetsSides.Start + WindowInsetsSides.End + WindowInsetsSides.Bottom
    )

    ModalDrawerSheet(
        drawerState = drawerState,
        windowInsets = WindowInsets()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .windowInsetsPadding(headerInsets)
        ) {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(96.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 0.dp, top = 8.dp)
            ) {
                Text(
                    text = serverConfig?.name ?: "",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1F)
                        .align(Alignment.CenterVertically)
                )
                ResourceIconButton(
                    drawableResId = R.drawable.ic_settings_24dp,
                    contentDescriptionResId = R.string.content_description_open_settings,
                    onClick = onOpenServerSetup,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .windowInsetsPadding(contentInsets)
                .verticalScroll(rememberScrollState())
        ) {
            if (connectedState != null) {
                connectedState.players.forEach { player ->
                    MainDrawerItem(
                        label = player.name,
                        iconResId = R.drawable.ic_player_24dp,
                        selected = player == connectedState.selectedPlayer,
                        drawerState = drawerState,
                        onClick = { onPlayerSelected(player.id) }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                MainDrawerItem(
                    label = stringResource(R.string.settings_category_local_player),
                    iconResId = null,
                    selected = false,
                    drawerState = drawerState,
                    onClick = {},
                    badgeContent = {
                        Switch(
                            checked = localPlaybackEnabled,
                            onCheckedChange = onLocalPlaybackEnabledChanged
                        )
                    }
                )
                MainDrawerItem(
                    label = stringResource(R.string.menu_manage_players),
                    iconResId = null,
                    selected = false,
                    drawerState = drawerState,
                    onClick = onManagePlayers
                )
            }
            MainDrawerItem(
                label = stringResource(R.string.menu_settings),
                iconResId = R.drawable.ic_settings_24dp,
                selected = false,
                drawerState = drawerState,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
fun MainDrawerItem(
    label: String,
    iconResId: Int?,
    selected: Boolean,
    drawerState: DrawerState,
    onClick: () -> Unit,
    badgeContent: @Composable () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    NavigationDrawerItem(
        label = {
            Text(text = label)
        },
        badge = badgeContent,
        icon = {
            if (iconResId != null) {
                Icon(
                    imageVector = ImageVector.vectorResource(iconResId),
                    contentDescription = label
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        selected = selected,
        onClick = {
            scope.launch {
                onClick()
                drawerState.close()
            }
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun MainActivityPlayerContent(
    drawerState: DrawerState,
    selectedPlayer: Player,
    serverConfigState: State<ServerConfiguration?>,
    visible: Boolean,
    forceGridForSlimBrowseItemsState: State<Boolean>,
    volumeChangeRequestFlow: SharedFlow<Int>,
    onDownloadSelected: (DownloadRequestData) -> Unit,
    onGalleryItemSelected: (SlideshowImage) -> Unit,
    onWebLinkSelected: (title: String, link: Uri) -> Unit
) = key(selectedPlayer.id) {
    val owner = rememberViewModelStoreOwner()

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        KoinScope(
            scopeDefinition = {
                getOrCreateScope<ActivePlayerScope>(scopeId = selectedPlayer.id.id).apply {
                    declare(selectedPlayer.id)
                }
            }
        ) {
            val mainContentViewModel = koinViewModel<MainContentViewModel>()
            val searchViewModel = koinViewModel<SearchViewModel>()
            val volumeViewModel = koinViewModel<VolumePopupViewModel>()
            val nowPlayingMotionState = rememberNowPlayingMotionState(false)
            val scope = rememberCoroutineScope()

            LaunchedEffect(volumeChangeRequestFlow, volumeViewModel) {
                volumeChangeRequestFlow.collect { amount ->
                    volumeViewModel.adjustVolume(amount)
                    volumeViewModel.show(2.seconds)
                }
            }

            BoxWithConstraints(
                modifier = if (visible) Modifier else Modifier.hideAndBlockInput()
            ) {
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                val systemInsets = ScaffoldDefaults.contentWindowInsets.asPaddingValues()
                val appBarColors = TopAppBarDefaults.topAppBarColors()

                val targetFraction by remember(scrollBehavior) {
                    derivedStateOf {
                        FastOutLinearInEasing.transform(scrollBehavior.state.overlappedFraction)
                    }
                }
                val shownFraction = remember { Animatable(targetFraction) }

                LaunchedEffect(scrollBehavior) {
                    snapshotFlow { targetFraction }.collectLatest { target ->
                        val jump = abs(target - shownFraction.value)
                        if (jump < 0.2f) {
                            // normal scrolling: follow the finger exactly
                            shownFraction.snapTo(target)
                        } else {
                            // page switch: ease over to the new value
                            shownFraction.animateTo(target, tween(durationMillis = 200))
                        }
                    }
                }

                val appBarColor = lerp(
                    start = appBarColors.containerColor,
                    stop = appBarColors.scrolledContainerColor,
                    fraction = shownFraction.value
                )

                val layout = determineMainLayout(
                    containerSize = DpSize(maxWidth, maxHeight),
                    insets = systemInsets,
                    layoutDirection = LocalLayoutDirection.current
                )

                val useGridForSlimBrowseItemsState = remember {
                    derivedStateOf {
                        forceGridForSlimBrowseItemsState.value ||
                            layout.useGridForSlimBrowseItemsByDefault
                    }
                }

                Scaffold(
                    topBar = {
                        MainActivityAppBar(
                            drawerState = drawerState,
                            scrollBehavior = scrollBehavior,
                            selectedPlayer = selectedPlayer,
                            colors = appBarColors.copy(
                                containerColor = appBarColor,
                                scrolledContainerColor = appBarColor
                            ),
                            onOpenSearch = searchViewModel::open,
                            contentPadding = layout.toolbarContentPadding,
                            windowInsets = WindowInsets(),
                            modifier = Modifier.padding(layout.toolbarPadding)
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        val searchOpen by searchViewModel.visibleFlow.collectAsStateWithLifecycle()

                        Breadcrumbs(
                            mainContentViewModel.breadcrumbsFlow,
                            serverConfig = serverConfigState.value,
                            onHomeClicked = mainContentViewModel::goToHome,
                            onGoBack = mainContentViewModel::goBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(appBarColor)
                                .padding(layout.breadcrumbsPadding)
                        )

                        Box(
                            modifier = Modifier.weight(1F)
                        ) {
                            MainContent(
                                viewModel = mainContentViewModel,
                                serverConfig = serverConfigState,
                                useGridForSlimBrowseItemsState = useGridForSlimBrowseItemsState,
                                slimBrowseMinGridCellSize = layout.slimBrowseGridCellMinSize,
                                pagePadding = layout.mainContentPagePadding,
                                compensateBottomPagePaddingDuringAnimation =
                                    layout.compensateMainContentBottomPagePaddingDuringAnimation,
                                appBarState = scrollBehavior.state,
                                navigationBackEnabled = !searchOpen,
                                onGalleryItemSelected = onGalleryItemSelected,
                                onDownloadSelected = onDownloadSelected,
                                onWebLinkSelected = onWebLinkSelected,
                                onGoToNowPlaying = {
                                    scope.launch {
                                        nowPlayingMotionState.nowPlaying.expand()
                                    }
                                },
                                modifier = Modifier
                                    .padding(layout.mainContentPadding)
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                            )

                            NavigationBarScrim(
                                color = MaterialTheme.colorScheme.surface,
                                insets = layout.mainContentPagePadding
                            )
                        }
                    }
                }

                NowPlayingScreen(
                    playerId = selectedPlayer.id,
                    layout = layout,
                    motionState = nowPlayingMotionState,
                    serverConfig = serverConfigState.value,
                    insets = systemInsets,
                    onShowVolumePopup = { volumeViewModel.show(5.seconds) },
                    onInfoMenuItemGoAction = { action, parentItem, contextItem ->
                        mainContentViewModel.goToHome()
                        mainContentViewModel.onNowPlayingContextMenuAction(
                            action,
                            contextItem,
                            parentItem
                        )
                    }
                )
                SearchBox(
                    viewModel = searchViewModel,
                    pillModifier = Modifier.padding(layout.searchBoxPadding),
                    onClose = searchViewModel::close,
                    onOpenResults = { query, type ->
                        if (type == null) {
                            mainContentViewModel.openRadioSearchResults(query)
                        } else {
                            mainContentViewModel.openLocalSearchResults(query, type)
                        }
                    }
                )
                VolumePopup(
                    viewModel = volumeViewModel
                )
                DisplayStatusIndicator(
                    serverConfig = serverConfigState.value
                )
            }
        }
    }
}

@Composable
private fun MainActivityAppBar(
    drawerState: DrawerState,
    selectedPlayer: Player?,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    onOpenSearch: (() -> Unit)?,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding
) {
    val scope = rememberCoroutineScope()

    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = colors,
        title = { Text(stringResource(R.string.app_name)) },
        subtitle = {
            selectedPlayer?.let { Text(it.name) }
        },
        titleHorizontalAlignment = Alignment.CenterHorizontally,
        navigationIcon = {
            ResourceIconButton(
                drawableResId = R.drawable.ic_drawer_menu_24dp,
                contentDescriptionResId = R.string.content_description_appbar_drawer,
                onClick = {
                    scope.launch {
                        drawerState.open()
                    }
                }
            )
        },
        actions = {
            if (onOpenSearch != null) {
                ResourceIconButton(
                    drawableResId = R.drawable.ic_search_24dp,
                    contentDescriptionResId = R.string.content_description_appbar_search,
                    onClick = onOpenSearch
                )
            }
        },
        windowInsets = windowInsets,
        contentPadding = contentPadding,
        modifier = modifier
    )
}

@Composable
private fun MainActivityLoadingIndicator(drawerState: DrawerState) = Scaffold(
    topBar = {
        MainActivityAppBar(
            drawerState = drawerState,
            selectedPlayer = null,
            onOpenSearch = null
        )
    }
) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        CircularProgressIndicator(
            strokeWidth = 6.dp,
            modifier = Modifier
                .size(128.dp)
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun MainActivityErrorContent(
    drawerState: DrawerState,
    textResId: Int,
    subtext: String?,
    buttons: List<ActionButtonSpec>,
    iconResId: Int = R.drawable.ic_cloud_question_24dp
) = Scaffold(
    topBar = {
        MainActivityAppBar(
            drawerState = drawerState,
            selectedPlayer = null,
            onOpenSearch = null
        )
    }
) { innerPadding ->
    ErrorHintContent(
        iconResId = iconResId,
        textResId = textResId,
        subtext = subtext,
        buttons = buttons,
        modifier = Modifier.padding(innerPadding)
    )
}

private fun determineMainLayout(
    containerSize: DpSize,
    insets: PaddingValues,
    layoutDirection: LayoutDirection
): MainLayout {
    val nowPlayingCornerSize = 16.dp

    return when {
        containerSize.width < containerSize.height -> {
            val collapsedNowPlayingHeight = 68.dp // 64dp sheet + 4dp progress indicator
            val mainContentPadding = PaddingValues(
                bottom = insets.calculateBottomPadding() +
                    collapsedNowPlayingHeight - nowPlayingCornerSize
            )
            val mainContentPagePadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
                bottom = nowPlayingCornerSize
            )
            val searchBoxPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection)
            )
            val breadcrumbsPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection)
            )
            val toolbarContentPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection)
            )
            MainLayout.Portrait(
                containerSize = containerSize,
                mainContentPadding = mainContentPadding,
                mainContentPagePadding = mainContentPagePadding,
                breadcrumbsPadding = breadcrumbsPadding,
                searchBoxPadding = searchBoxPadding,
                toolbarPadding = PaddingValues(),
                toolbarContentPadding = toolbarContentPadding,
                collapsedNowPlayingHeight = collapsedNowPlayingHeight,
                nowPlayingCornerSize = nowPlayingCornerSize
            )
        }

        else -> {
            val collapsedNowPlayingWidth = max(containerSize.width * 0.3F, 320.dp)
            val mainContentPadding = PaddingValues(
                end = collapsedNowPlayingWidth
            )
            val mainContentPagePadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = nowPlayingCornerSize,
                bottom = insets.calculateBottomPadding()
            )
            val breadcrumbsPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = collapsedNowPlayingWidth + insets.calculateEndPadding(layoutDirection)
            )
            val searchBoxPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                start = insets.calculateStartPadding(layoutDirection),
                end = collapsedNowPlayingWidth + insets.calculateEndPadding(layoutDirection)
            )
            val toolbarPadding = PaddingValues(
                end = collapsedNowPlayingWidth - nowPlayingCornerSize
            )
            val toolbarContentPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                start = insets.calculateStartPadding(layoutDirection),
                end = nowPlayingCornerSize
            )
            MainLayout.Landscape(
                containerSize = containerSize,
                mainContentPadding = mainContentPadding,
                mainContentPagePadding = mainContentPagePadding,
                breadcrumbsPadding = breadcrumbsPadding,
                searchBoxPadding = searchBoxPadding,
                toolbarPadding = toolbarPadding,
                toolbarContentPadding = toolbarContentPadding,
                collapsedNowPlayingWidth = collapsedNowPlayingWidth,
                nowPlayingCornerSize = nowPlayingCornerSize
            )
        }
    }
}

private fun Modifier.hideAndBlockInput(): Modifier = this
    .alpha(0F)
    .clearAndSetSemantics { }
    .focusProperties { canFocus = false }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                    .changes.forEach { it.consume() }
            }
        }
    }

@Preview(widthDp = 360, heightDp = 800)
@Composable
fun MainActivityUiPreview() {
    val players = listOf(
        Player(
            id = PlayerId("00:11:22:33:44:55"),
            ip = "1.2.3.4",
            name = "First player",
            model = "Squeezebox",
            canPowerOff = true,
            connected = true
        ),
        Player(
            id = PlayerId("11:22:33:44:55::66"),
            ip = "1.2.3.5",
            name = "Second player",
            model = "Squeezebox",
            canPowerOff = true,
            connected = true
        )
    )
    val state = MainActivityState.Connected(
        serverConfig = ServerConfiguration(
            name = "Test",
            hostnameAndPort = "1.2.3.4:9000",
            username = null,
            password = null
        ),
        players = players,
        selectedPlayer = players[1]
    )

    MainActivityUi(
        stateFlow = MutableStateFlow(state),
        forceGridForSlimBrowseItemsFlow = flowOf(false),
        localPlaybackEnabledFlow = flowOf(false),
        volumeChangeRequestFlow = MutableSharedFlow()
    )
}
