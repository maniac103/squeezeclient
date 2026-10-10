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
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.ui.common.BottomSheetSceneStrategy
import kotlinx.coroutines.flow.filterIsInstance

data class BreadcrumbsState(val items: List<BreadcrumbsItem>, val backProgress: Float?)

@Composable
fun MainContent(
    viewModel: MainContentViewModel,
    serverConfig: State<ServerConfiguration?>,
    useGridForSlimbrowseItems: State<Boolean>,
    bottomPagePadding: Dp,
    modifier: Modifier = Modifier,
    onScrollStateChanged: (canScrollUp: Boolean) -> Unit = {},
    onBreadcrumbsChanged: (BreadcrumbsState) -> Unit = {},
    onGalleryItemSelected: (SlideshowImage) -> Unit = {},
    onDownloadSelected: (DownloadRequestData) -> Unit = {},
    onWebLinkSelected: (title: String, link: Uri) -> Unit = { _, _ -> },
    onGoToNowPlaying: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(viewModel.initialPage)
    val nestedScrollInterop = rememberNestedScrollInteropConnection()

    val removeFromBackStack = { route: NavKey ->
        backStack.removeAll { it == route }
        Unit
    }
    val refreshFlow = viewModel.navigationFlow
        .filterIsInstance<MainContentNavigation.RefreshContent>()

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberAppBarScrollNavEntryDecorator(onScrollStateChanged)
        ),
        entryProvider = entryProvider {
            jiveHomeMenuNavEntry(
                bottomPagePadding = bottomPagePadding,
                onItemSelected = viewModel::onHomeItemSelected
            )

            slimBrowseNavEntry(
                serverConfig = serverConfig,
                backStack = backStack,
                refreshFlow = refreshFlow,
                useGridIfPossible = useGridForSlimbrowseItems,
                bottomPagePadding = bottomPagePadding,
                onItemSelected = viewModel::onSlimBrowseItemSelected,
                onContextMenuItemSelected = viewModel::onSlimBrowseContextMenu
            )

            slimBrowseSubItemsNavEntry(
                serverConfig = serverConfig,
                bottomPagePadding = bottomPagePadding,
                onItemSelected = viewModel::onSlimBrowseItemSelected,
                onContextMenuItemSelected = viewModel::onSlimBrowseContextMenu
            )

            galleryNavEntry(
                serverConfig = serverConfig,
                bottomPagePadding = bottomPagePadding,
                onItemSelected = onGalleryItemSelected
            )

            localSearchResultsNavKey(
                serverConfig = serverConfig,
                useGrid = useGridForSlimbrowseItems,
                bottomPagePadding = bottomPagePadding,
                onItemSelected = { item -> viewModel.onSlimBrowseItemSelected(item, null) },
                onContextMenuItemSelected = viewModel::onSlimBrowseContextMenu
            )
            radioSearchResultsNavKey(
                serverConfig = serverConfig,
                useGrid = useGridForSlimbrowseItems,
                bottomPagePadding = bottomPagePadding,
                onItemSelected = { item -> viewModel.onSlimBrowseItemSelected(item, null) },
                onContextMenuItemSelected = viewModel::onSlimBrowseContextMenu
            )

            homeMenuInputNavEntry(
                onInputSubmitted = viewModel::onHomeItemInputSubmitted,
                onDone = removeFromBackStack
            )
            homeMenuTimePickerNavEntry(
                onInputSubmitted = viewModel::onHomeItemInputSubmitted,
                onDone = removeFromBackStack
            )
            slimBrowseInputNavEntry(
                onInputSubmitted = viewModel::onSlimBrowseInputSubmitted,
                onDone = removeFromBackStack
            )
            slimBrowseTimePickerNavEntry(
                onInputSubmitted = viewModel::onSlimBrowseInputSubmitted,
                onDone = removeFromBackStack
            )
            sliderInputNavEntry(
                onValueChanged = viewModel::onSliderValueChanged,
                onDone = removeFromBackStack
            )
            homeMenuChoicesNavEntry(
                onChoiceSelected = viewModel::onHomeItemChoiceSelected,
                onDone = removeFromBackStack
            )
            slimBrowseChoicesNavEntry(
                onChoiceSelected = viewModel::onSlimBrowseChoiceSelected,
                onDone = removeFromBackStack
            )
            infoNavEntry()

            contextMenuNavEntry(
                serverConfig = serverConfig,
                onItemSelected = { item, parentItem ->
                    val downloadData = viewModel.extractDownloadDataForContextMenuItem(item)
                    if (downloadData != null) {
                        onDownloadSelected(downloadData)
                        null
                    } else {
                        viewModel.onContextMenuItemSelected(item, parentItem)
                    }
                },
                onDone = removeFromBackStack
            )

            itemActionsNavEntry(
                serverConfig = serverConfig,
                onDownloadSelected = onDownloadSelected,
                onActionSelected = viewModel::onItemActionSelected,
                onDone = removeFromBackStack
            )
        }
    )

    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = listOf(
            DialogSceneStrategy(),
            BottomSheetSceneStrategy(),
            SinglePaneSceneStrategy()
        ),
        onBack = {
            backStack.removeLastOrNull()
        }
    )

    val navigationEventState = rememberNavigationEventState(sceneState)

    val isPredictiveBack = navigationEventState.transitionState.let { state ->
        state is NavigationEventTransitionState.InProgress &&
                state.direction == NavigationEventTransitionState.TRANSITIONING_BACK
    }
    val animationType = when {
        isPredictiveBack -> MainContentAnimationType.PredictiveBack
        else -> MainContentAnimationType.Normal
    }
    val animationState = MainContentAnimationState(animationType, bottomPagePadding)

    LaunchedEffect(Unit) {
        viewModel.navigationFlow.collect { destination ->
            when (destination) {
                is MainContentNavigation.Home -> {
                    backStack.subList(1, backStack.size).clear()
                }

                is MainContentNavigation.NowPlaying -> {
                    backStack.subList(1, backStack.size).clear()
                    onGoToNowPlaying()
                }

                is MainContentNavigation.GoBack -> {
                    repeat(minOf(destination.levels, backStack.size - 1)) {
                        backStack.removeLastOrNull()
                    }
                }

                is MainContentNavigation.RefreshContent -> {
                    // handled via refreshFlow above
                }

                is MainContentNavigation.WebLink ->
                    onWebLinkSelected(destination.title, destination.link.toUri())

                else -> backStack.add(destination)
            }
        }
    }

    LaunchedEffect(backStack, navigationEventState) {
        snapshotFlow {
            val progress = when (val transition = navigationEventState.transitionState) {
                is NavigationEventTransitionState.InProgress -> transition.latestEvent.progress
                is NavigationEventTransitionState.Idle -> null
            }

            val relevantEntries = backStack.subList(1, backStack.size)
            val breadcrumbs = relevantEntries.mapNotNull {
                when (it) {
                    is MainContentNavigation.JiveHomeMenuPage -> {
                        BreadcrumbsItem(it.title, null, null)
                    }

                    is MainContentNavigation.SlimBrowseItemPage -> {
                        BreadcrumbsItem(it.title, it.parentTitle, it.icon)
                    }

                    is MainContentNavigation.SlimBrowseSubItemPage -> {
                        BreadcrumbsItem(it.title, null, null)
                    }

                    is MainContentNavigation.GalleryPage -> {
                        BreadcrumbsItem(it.title, it.parentTitle, null)
                    }

                    else -> null
                }
            }

            BreadcrumbsState(breadcrumbs, progress)
        }.collect { state ->
            onBreadcrumbsChanged(state)
        }
    }

    NavigationBackHandler(
        sceneState = sceneState,
        state = navigationEventState,
        onBackCompleted = {
            // Pop the same entries that SceneState determined should disappear.
            repeat(entries.size - sceneState.currentScene.previousEntries.size) {
                backStack.removeLastOrNull()
            }
        },
        onBackCancelled = {
        }
    )

    CompositionLocalProvider(LocalMainContentAnimationState provides animationState) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = modifier.nestedScroll(nestedScrollInterop)
        ) {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = navigationEventState,
                transitionSpec = { mainContentEnter() togetherWith mainContentExit() },
                popTransitionSpec = { mainContentPopEnter() togetherWith mainContentPopExit() },
                predictivePopTransitionSpec = {
                    mainContentPopEnter() togetherWith mainContentPopExit()
                }
            )
        }
    }
}
