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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.model.WindowStyle
import de.maniac103.squeezeclient.ui.bottomsheets.ChoicesBottomSheet
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheet
import de.maniac103.squeezeclient.ui.bottomsheets.InputTimePicker
import de.maniac103.squeezeclient.ui.bottomsheets.SliderBottomSheet
import de.maniac103.squeezeclient.ui.common.BottomSheetSceneStrategy
import de.maniac103.squeezeclient.ui.composables.AppBarScrollReporter
import de.maniac103.squeezeclient.ui.composables.BottomSheetContentWrapper
import de.maniac103.squeezeclient.ui.composables.LocalAppBarScrollReporter
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetContent
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetViewModel
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsSheet
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsViewModel
import de.maniac103.squeezeclient.ui.itemlist.BasePagedSlimBrowseItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeItemList
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListMode
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemList
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemListViewModel
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsItemList
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsViewModel
import de.maniac103.squeezeclient.ui.search.RadioSearchResultViewModel
import de.maniac103.squeezeclient.ui.search.RadioSearchResultsItemList
import de.maniac103.squeezeclient.ui.slideshow.GalleryGrid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun rememberAppBarScrollNavEntryDecorator(onScrollStateChanged: (Boolean) -> Unit) =
    NavEntryDecorator<NavKey>(
        decorate = {
            val reporter = remember {
                object : AppBarScrollReporter {
                    override fun setCanScrollUp(canScrollUp: Boolean) {
                        onScrollStateChanged(canScrollUp)
                    }
                }
            }

            CompositionLocalProvider(LocalAppBarScrollReporter provides reporter) {
                it.Content()
            }
        }
    )

@Composable
fun RefreshNavigationObserver(
    route: NavKey,
    backStack: List<NavKey>,
    refreshFlow: Flow<MainContentNavigation.RefreshContent>,
    viewModel: BasePagedSlimBrowseItemListViewModel
) {
    LaunchedEffect(viewModel, backStack) {
        val ownIndex = backStack.indexOf(route)
        val depthFromTop = backStack.size - ownIndex
        refreshFlow.collect { event ->
            if (depthFromTop in 1..event.levels) {
                viewModel.refresh()
            }
        }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.jiveHomeMenuNavEntry(
    bottomPagePadding: Dp,
    onItemSelected: (JiveHomeMenuItem) -> Unit
) = entry<MainContentNavigation.JiveHomeMenuPage> { route ->
    val viewModel = koinViewModel<JiveHomeItemListViewModel> {
        parametersOf(route.playerId, route.nodeId)
    }
    MainContentPage(bottomPagePadding) {
        JiveHomeItemList(viewModel) { selectedItem ->
            onItemSelected(selectedItem)
        }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.slimBrowseNavEntry(
    serverConfig: State<ServerConfiguration?>,
    backStack: List<NavKey>,
    refreshFlow: Flow<MainContentNavigation.RefreshContent>,
    useGridIfPossible: State<Boolean>,
    bottomPagePadding: Dp,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem, JiveAction) -> Job?,
    onContextMenuItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.SlimBrowseItemPage> { route ->
    val viewModel = koinViewModel<SlimBrowseItemListViewModel> {
        parametersOf(route.playerId, route.fetchAction)
    }

    val canUseGrid = route.windowStyle == WindowStyle.IconList ||
            route.windowStyle == WindowStyle.HomeMenu
    val showIcons = route.windowStyle != null && route.windowStyle != WindowStyle.TextOnlyList
    val mode = when {
        useGridIfPossible.value && canUseGrid -> SlimBrowseItemListMode.Grid
        showIcons -> SlimBrowseItemListMode.ListWithIcons
        else -> SlimBrowseItemListMode.ListWithoutIcons
    }

    RefreshNavigationObserver(
        route = route,
        backStack = backStack,
        refreshFlow = refreshFlow,
        viewModel = viewModel
    )

    MainContentPage(bottomPagePadding) {
        SlimBrowseItemList(
            viewModel,
            serverConfig.value,
            mode,
            itemSelectionListener = { selectedItem ->
                onItemSelected(selectedItem, route.fetchAction)?.let { job ->
                    viewModel.setItemBusy(selectedItem, job)
                }
            },
            contextMenuClickListener = { item ->
                onContextMenuItemSelected(item)?.let { job ->
                    viewModel.setItemBusy(item, job)
                }
            }
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.slimBrowseSubItemsNavEntry(
    serverConfig: State<ServerConfiguration?>,
    bottomPagePadding: Dp,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem, JiveAction) -> Job?,
    onContextMenuItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.SlimBrowseSubItemPage> { route ->
    val viewModel = koinViewModel<SlimBrowseSubItemListViewModel> {
        parametersOf(route.playerId, route.fetchAction, route.position)
    }

    MainContentPage(bottomPagePadding) {
        SlimBrowseSubItemList(
            viewModel,
            serverConfig.value,
            itemSelectionListener = { selectedItem ->
                onItemSelected(selectedItem, route.fetchAction)
                    ?.let { job -> viewModel.setItemBusy(selectedItem, job) }
            },
            contextMenuClickListener = { item ->
                onContextMenuItemSelected(item)
                    ?.let { job -> viewModel.setItemBusy(item, job) }
            }
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.galleryNavEntry(
    serverConfig: State<ServerConfiguration?>,
    bottomPagePadding: Dp,
    onItemSelected: (SlideshowImage) -> Unit
) = entry<MainContentNavigation.GalleryPage> { route ->
    MainContentPage(bottomPagePadding) {
        GalleryGrid(route.items, serverConfig.value) { item ->
            onItemSelected(item)
        }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.localSearchResultsNavKey(
    serverConfig: State<ServerConfiguration?>,
    useGrid: State<Boolean>,
    bottomPagePadding: Dp,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onContextMenuItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.LocalSearchResults> { route ->
    val viewModel = koinViewModel<LibrarySearchResultsViewModel> {
        parametersOf(route.playerId, route.searchTerm, route.mode)
    }

    MainContentPage(bottomPagePadding) {
        LibrarySearchResultsItemList(
            viewModel,
            serverConfig.value,
            useGrid.value,
            itemSelectionListener = { item ->
                onItemSelected(item)
                    ?.let { job -> viewModel.setItemBusy(item, job) }
            },
            contextMenuClickListener = { item ->
                onContextMenuItemSelected(item)
                    ?.let { job -> viewModel.setItemBusy(item, job) }
            }
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.radioSearchResultsNavKey(
    serverConfig: State<ServerConfiguration?>,
    useGrid: State<Boolean>,
    bottomPagePadding: Dp,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onContextMenuItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.RadioSearchResults> { route ->
    val viewModel = koinViewModel<RadioSearchResultViewModel> {
        parametersOf(route.playerId, route.searchTerm)
    }

    MainContentPage(bottomPagePadding) {
        RadioSearchResultsItemList(
            viewModel,
            serverConfig.value,
            useGrid.value,
            itemSelectionListener = { item ->
                onItemSelected(item)
                    ?.let { job -> viewModel.setItemBusy(item, job) }
            },
            contextMenuClickListener = { item ->
                onContextMenuItemSelected(item)
                    ?.let { job -> viewModel.setItemBusy(item, job) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.homeMenuInputNavEntry(
    onInputSubmitted: (title: String, action: JiveAction, isGoAction: Boolean) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.HomeMenuInput>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    InputBottomSheet(route.title, route.input) { action ->
        onInputSubmitted(route.title, action, route.input.actionHasTarget)
            .also { it.invokeOnDoneOrNull { onDone(route) } }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.homeMenuTimePickerNavEntry(
    onInputSubmitted: (title: String, action: JiveAction, isGoAction: Boolean) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.HomeMenuTimePicker>(
    metadata = DialogSceneStrategy.dialog()
) { route ->
    InputTimePicker(
        route.title,
        route.input,
        onInputSubmitted = { action -> onInputSubmitted(route.title, action, route.input.actionHasTarget) },
        onClose = { onDone(route) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.slimBrowseInputNavEntry(
    onInputSubmitted: (item: SlimBrowseItemList.SlimBrowseItem, action: JiveAction, isGoAction: Boolean) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SlimBrowseInput>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    InputBottomSheet(route.item.title, route.input) { action ->
        onInputSubmitted(route.item, action, route.input.actionHasTarget)
            .also { it.invokeOnDoneOrNull { onDone(route) } }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.slimBrowseTimePickerNavEntry(
    onInputSubmitted: (item: SlimBrowseItemList.SlimBrowseItem, action: JiveAction, isGoAction: Boolean) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SlimBrowseTimePicker>(
    metadata = DialogSceneStrategy.dialog()
) { route ->
    InputTimePicker(
        route.item.title,
        route.input,
        onInputSubmitted = { action -> onInputSubmitted(route.item, action, route.input.actionHasTarget) },
        onClose = { onDone(route) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.sliderInputNavEntry(
    onValueChanged: (JiveAction) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SliderInput>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    SliderBottomSheet(route.title, route.slider) { action ->
        onValueChanged(action)
            .also { it.invokeOnDoneOrNull { onDone(route) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.homeMenuChoicesNavEntry(
    onChoiceSelected: (JiveAction) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.HomeMenuChoices>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    ChoicesBottomSheet(route.parentTitle, route.choices) { action ->
        onChoiceSelected(action)
            .also { it.invokeOnDoneOrNull { onDone(route) }
        }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.slimBrowseChoicesNavEntry(
    onChoiceSelected: (JiveAction, SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SlimBrowseChoices> { route ->
    ChoicesBottomSheet(route.item.title, route.choices) { action ->
        onChoiceSelected(action, route.item)
            .also { it.invokeOnDoneOrNull { onDone(route) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.infoNavEntry() = entry<MainContentNavigation.Info>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    BottomSheetContentWrapper(route.title, false) {
        Text(route.info)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.contextMenuNavEntry(
    serverConfig: State<ServerConfiguration?>,
    onItemSelected: (item: SlimBrowseItemList.SlimBrowseItem, parentItem: SlimBrowseItemList.SlimBrowseItem?) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.ContextMenu>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    val viewModel = koinViewModel<ContextMenuBottomSheetViewModel> {
        parametersOf(route.playerId, route.menuItems, route.item)
    }

    ContextMenuBottomSheetContent(viewModel, serverConfig.value) { item ->
        onItemSelected(item, route.item)
            ?.also { viewModel.setItemBusy(item, it) }
            .invokeOnDoneOrNull { onDone(route) }
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.itemActionsNavEntry(
    serverConfig: State<ServerConfiguration?>,
    onDownloadSelected: (DownloadRequestData) -> Unit,
    onActionSelected: (JiveAction, SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.ItemActions> { route ->
    val viewModel = koinViewModel<ItemActionsViewModel> { parametersOf(route.item) }

    ItemActionsSheet(viewModel, serverConfig.value) { action ->
        if (action.download != null) {
            onDownloadSelected(action.download)
        } else {
            action.action
                ?.let { onActionSelected(it, route.item) }
                ?.also { viewModel.setActionBusy(action, it) }
                .invokeOnDoneOrNull { onDone(route) }
        }
    }
}

internal fun Job?.invokeOnDoneOrNull(onDone: () -> Unit) {
    if (this == null) {
        onDone()
    } else {
        invokeOnCompletion { onDone() }
    }
}