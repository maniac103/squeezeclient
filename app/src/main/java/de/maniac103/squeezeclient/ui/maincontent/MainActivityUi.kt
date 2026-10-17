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
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.Player
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.ui.ActivePlayerScope
import de.maniac103.squeezeclient.ui.displaystatus.DisplayStatusIndicator
import de.maniac103.squeezeclient.ui.nowplaying.NowPlayingScreen
import de.maniac103.squeezeclient.ui.nowplaying.rememberNowPlayingMotionState
import de.maniac103.squeezeclient.ui.search.SearchBox
import de.maniac103.squeezeclient.ui.search.SearchViewModel
import de.maniac103.squeezeclient.ui.volume.VolumeControlPopup
import de.maniac103.squeezeclient.ui.volume.VolumePopupViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.scope.KoinScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds

sealed interface MainLayout {
    val containerSize: DpSize
    val mainContentPadding: PaddingValues
    val mainContentPagePadding: PaddingValues
    val nowPlayingCornerSize: Dp
    val useGridForSlimBrowseItems: Boolean

    data class Portrait(
        override val containerSize: DpSize,
        override val mainContentPadding: PaddingValues,
        override val mainContentPagePadding: PaddingValues,
        val collapsedNowPlayingHeight: Dp,
        override val nowPlayingCornerSize: Dp
    ) : MainLayout {
        override val useGridForSlimBrowseItems = false
    }

    data class Landscape(
        override val containerSize: DpSize,
        override val mainContentPadding: PaddingValues,
        override val mainContentPagePadding: PaddingValues,
        val collapsedNowPlayingWidth: Dp,
        override val nowPlayingCornerSize: Dp
    ) : MainLayout {
        override val useGridForSlimBrowseItems = true
    }
}

@Composable
fun MainActivityUi(
    volumeChangeRequestFlow: SharedFlow<Float>,
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
        serverConfigFlow = viewModel.serverConfigFlow,
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
    serverConfigFlow: Flow<ServerConfiguration?>,
    forceGridForSlimBrowseItemsFlow: Flow<Boolean>,
    localPlaybackEnabledFlow: Flow<Boolean>,
    volumeChangeRequestFlow: SharedFlow<Float>,
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
    val state by stateFlow.collectAsState()

    val serverConfigState = serverConfigFlow.collectAsState(null)
    val forceGridForSlimBrowseItemsState = forceGridForSlimBrowseItemsFlow.collectAsState(false)
    val localPlaybackEnabledState = localPlaybackEnabledFlow.collectAsState(false)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainActivityDrawerContent(
                drawerState = drawerState,
                serverConfig = serverConfigState.value,
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
        Scaffold { innerPadding ->
            when (val state = state) {
                is MainActivityState.Loading -> {
                    MainActivityLoadingIndicator(
                        drawerState = drawerState
                    )
                }

                is MainActivityState.CouldNotConnect -> {
                    ErrorHintContent(
                        iconResId = R.drawable.ic_cloud_question_24dp,
                        textResId = R.string.connection_error_text_no_connection,
                        subtext = null,
                        buttons = listOf(
                            ActionButtonSpec(R.string.connection_error_action_retry, onRetryConnection),
                            ActionButtonSpec(R.string.connection_error_action_setup_server, onOpenServerSetup)
                        )
                    )
                }

                is MainActivityState.ConnectionFailed -> {
                    ErrorHintContent(
                        iconResId = R.drawable.ic_cloud_question_24dp,
                        textResId = R.string.connection_error_text_connection_failure,
                        subtext = state.cause.message,
                        buttons = listOf(
                            ActionButtonSpec(R.string.connection_error_action_retry, onRetryConnection)
                        )
                    )
                }

                is MainActivityState.NoPlayers -> {
                    ErrorHintContent(
                        iconResId = R.drawable.ic_player_16dp,
                        textResId = R.string.connection_error_text_no_player,
                        subtext = null,
                        buttons = emptyList()
                    )
                }

                is MainActivityState.Connected -> {
                    MainActivityConnected(
                        drawerState = drawerState,
                        selectedPlayer = state.selectedPlayer,
                        insets = innerPadding,
                        serverConfigState = serverConfigState,
                        forceGridForSlimBrowseItemsState = forceGridForSlimBrowseItemsState,
                        volumeChangeRequestFlow = volumeChangeRequestFlow,
                        onGalleryItemSelected = onGalleryItemSelected,
                        onDownloadSelected = onDownloadSelected,
                        onWebLinkSelected = onWebLinkSelected
                    )
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
    val headerInsets = drawerInsets.only(WindowInsetsSides.Top)
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
                IconButton(
                    onClick = onOpenServerSetup,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_settings_24dp),
                        contentDescription = null // FIXME
                    )
                }
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
                        iconResId = R.drawable.ic_player_16dp,
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
            Box(
                modifier = Modifier.size(24.dp)
            ) {
                if (iconResId != null) {
                    Icon(
                        imageVector = ImageVector.vectorResource(iconResId),
                        contentDescription = label,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
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
private fun MainActivityConnected(
    drawerState: DrawerState,
    selectedPlayer: Player,
    insets: PaddingValues,
    serverConfigState: State<ServerConfiguration?>,
    forceGridForSlimBrowseItemsState: State<Boolean>,
    volumeChangeRequestFlow: SharedFlow<Float>,
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
            val searchViewModel = koinViewModel<SearchViewModel> { parametersOf(selectedPlayer.id) }
            val volumeViewModel = koinViewModel<VolumePopupViewModel> { parametersOf(selectedPlayer.id) }
            val nowPlayingMotionState = rememberNowPlayingMotionState(false)
            val scope = rememberCoroutineScope()

            LaunchedEffect(volumeChangeRequestFlow, volumeViewModel) {
                volumeChangeRequestFlow.collect { amount ->
                    volumeViewModel.adjustVolume(amount)
                    volumeViewModel.show(2.seconds)
                }
            }

            BoxWithConstraints {
                val layout = determineMainLayout(
                    containerSize = DpSize(maxWidth, maxHeight),
                    insets = insets,
                    layoutDirection = LocalLayoutDirection.current
                )

                val useGridForSlimBrowseItemsState = remember {
                    derivedStateOf {
                        forceGridForSlimBrowseItemsState.value || layout.useGridForSlimBrowseItems
                    }
                }

                Column {
                    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                    val appBarColors = TopAppBarDefaults.topAppBarColors()
                    val appBarColor by remember(appBarColors, scrollBehavior) {
                        derivedStateOf {
                            val overlappingFraction = scrollBehavior.state.overlappedFraction
                            lerp(
                                start = appBarColors.containerColor,
                                stop = appBarColors.scrolledContainerColor,
                                fraction = FastOutLinearInEasing.transform(overlappingFraction)
                            )
                        }
                    }

                    MainActivityAppBar(
                        drawerState = drawerState,
                        scrollBehavior = scrollBehavior,
                        selectedPlayer = selectedPlayer,
                        colors = appBarColors.copy(
                            containerColor = appBarColor,
                            scrolledContainerColor = appBarColor
                        ),
                        onOpenSearch = searchViewModel::open
                    )
                    Breadcrumbs(
                        mainContentViewModel.breadcrumbsFlow,
                        serverConfig = serverConfigState.value,
                        onHomeClicked = mainContentViewModel::goToHome,
                        onGoBack = mainContentViewModel::goBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appBarColor)
                    )
                    MainContent(
                        viewModel = mainContentViewModel,
                        serverConfig = serverConfigState,
                        useGridForSlimbrowseItems = useGridForSlimBrowseItemsState,
                        pagePadding = layout.mainContentPagePadding,
                        onGalleryItemSelected = onGalleryItemSelected,
                        onDownloadSelected = onDownloadSelected,
                        onWebLinkSelected = onWebLinkSelected,
                        onGoToNowPlaying = {
                            scope.launch {
                                nowPlayingMotionState.nowPlaying.expand()
                            }
                        },
                        modifier = Modifier
                            .weight(1F)
                            .padding(layout.mainContentPadding)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    )
                }

                NowPlayingScreen(
                    playerId = selectedPlayer.id,
                    layout = layout,
                    motionState = nowPlayingMotionState,
                    serverConfig = serverConfigState.value,
                    insets = insets,
                    onShowVolumePopup = { volumeViewModel.show(5.seconds) },
                    onInfoMenuItemGoAction = { action, parentItem, contextItem ->
                        mainContentViewModel.goToHome()
                        mainContentViewModel.onNowPlayingContextMenuAction(action, contextItem, parentItem)
                    }
                )
                SearchBox(
                    viewModel = searchViewModel,
                    pillModifier = Modifier.padding(insets),
                    onCloseListener = searchViewModel::close,
                    onOpenResultsListener = { query, type ->
                        if (type == null) {
                            mainContentViewModel.openRadioSearchResults(query)
                        } else {
                            mainContentViewModel.openLocalSearchResults(query, type)
                        }
                    }
                )
                VolumeControlPopup(
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
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    onOpenSearch: (() -> Unit)?
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
            IconButton(
                onClick = {
                    scope.launch {
                        drawerState.open()
                    }
                }
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_drawer_menu_24dp),
                    contentDescription = null // FIXME
                )
            }
        },
        actions = {
            if (onOpenSearch != null) {
                IconButton(
                    onClick = onOpenSearch
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_search_24dp),
                        contentDescription = null // FIXME
                    )
                }
            }
        }
    )
}

@Composable
private fun MainActivityLoadingIndicator(
    drawerState: DrawerState
) = Column(
    modifier = Modifier.fillMaxSize()
) {
    MainActivityAppBar(
        drawerState = drawerState,
        selectedPlayer = null,
        onOpenSearch = null
    )
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(
            strokeWidth = 6.dp,
            modifier = Modifier
                .size(128.dp)
                .align(Alignment.Center)
        )
    }
}

private fun determineMainLayout(
    containerSize: DpSize,
    insets: PaddingValues,
    layoutDirection: LayoutDirection
): MainLayout {
    val nowPlayingCornerSize = 16.dp

    return when {
        containerSize.width < containerSize.height -> {
            val collapsedNowPlayingHeight = 64.dp
            val mainContentPadding = PaddingValues(
                bottom = insets.calculateBottomPadding() +
                        collapsedNowPlayingHeight - nowPlayingCornerSize
            )
            val mainContentPagePadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
                bottom = nowPlayingCornerSize
            )
            MainLayout.Portrait(
                containerSize = containerSize,
                mainContentPadding = mainContentPadding,
                mainContentPagePadding = mainContentPagePadding,
                collapsedNowPlayingHeight = collapsedNowPlayingHeight,
                nowPlayingCornerSize = nowPlayingCornerSize
            )
        }

        else -> {
            val collapsedNowPlayingWidth = 320.dp
            val mainContentPadding = PaddingValues(
                end = collapsedNowPlayingWidth + insets.calculateEndPadding(layoutDirection),
            )
            val mainContentPagePadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = nowPlayingCornerSize,
                bottom = insets.calculateBottomPadding()
            )
            MainLayout.Landscape(
                containerSize = containerSize,
                mainContentPadding = mainContentPadding,
                mainContentPagePadding = mainContentPagePadding,
                collapsedNowPlayingWidth = collapsedNowPlayingWidth,
                nowPlayingCornerSize = nowPlayingCornerSize
            )

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
    MainActivityUi(
        stateFlow = MutableStateFlow(MainActivityState.Connected(players, players[1])),
        serverConfigFlow = flowOf(null),
        forceGridForSlimBrowseItemsFlow = flowOf(false),
        localPlaybackEnabledFlow = flowOf(false),
        volumeChangeRequestFlow = MutableSharedFlow()
    )
}