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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import de.maniac103.squeezeclient.extfuncs.invokeOnDoneOrNull
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.model.WindowStyle
import de.maniac103.squeezeclient.ui.bottomsheets.BottomSheetContentWrapper
import de.maniac103.squeezeclient.ui.bottomsheets.ChoicesBottomSheetContent
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetContent
import de.maniac103.squeezeclient.ui.bottomsheets.SliderBottomSheetContent
import de.maniac103.squeezeclient.ui.composables.InputTimePicker
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetContent
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuViewModel
import de.maniac103.squeezeclient.ui.contextmenu.ItemAction
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsMenuSheetContent
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsViewModel
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeItemList
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListOrGrid
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemList
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemListViewModel
import de.maniac103.squeezeclient.ui.itemlist.rememberSlimBrowseItemListMode
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsItemList
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsViewModel
import de.maniac103.squeezeclient.ui.search.RadioSearchResultsItemList
import de.maniac103.squeezeclient.ui.search.RadioSearchResultsViewModel
import de.maniac103.squeezeclient.ui.slideshow.GalleryGrid
import kotlinx.coroutines.Job
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun EntryProviderScope<NavKey>.JiveHomeMenuNavEntry(
    serverConfig: State<ServerConfiguration?>,
    pagePadding: PaddingValues,
    appBarState: TopAppBarState?,
    onItemSelected: (JiveHomeMenuItem) -> Unit
) = entry<MainContentNavigation.JiveHomeMenuPage> { route ->
    val viewModel = koinViewModel<JiveHomeItemListViewModel> { parametersOf(route.nodeId) }
    val listState = rememberLazyListState()
    MainContentPage(
        appBarState = appBarState,
        indicatorState = listState.scrollIndicatorState
    ) {
        JiveHomeItemList(
            state = listState,
            serverConfig = serverConfig.value,
            viewModel = viewModel,
            contentPadding = pagePadding,
            onItemSelected = onItemSelected
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.SlimBrowseNavEntry(
    serverConfig: State<ServerConfiguration?>,
    refreshTracker: RefreshTracker,
    useGridIfPossible: State<Boolean>,
    gridCellMinSize: Dp,
    pagePadding: PaddingValues,
    appBarState: TopAppBarState?,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem, JiveAction) -> Job?,
    onItemContextMenuSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.SlimBrowseItemPage> { route ->
    val viewModel = koinViewModel<SlimBrowseItemListViewModel> { parametersOf(route.fetchAction) }
    val canUseGrid =
        route.windowStyle == WindowStyle.IconList || route.windowStyle == WindowStyle.HomeMenu
    val mode = rememberSlimBrowseItemListMode(
        useGrid = useGridIfPossible.value && canUseGrid,
        showIcons = route.windowStyle != null && route.windowStyle != WindowStyle.TextOnlyList,
        gridCellMinSize = gridCellMinSize
    )

    val isStale = refreshTracker.isStale(route)
    LaunchedEffect(isStale) {
        if (isStale && refreshTracker.consume(route)) {
            viewModel.refresh()
        }
    }

    MainContentPage(
        appBarState = appBarState,
        indicatorState = mode.scrollIndicatorState
    ) {
        SlimBrowseItemListOrGrid(
            viewModel = viewModel,
            serverConfig = serverConfig.value,
            mode = mode,
            contentPadding = pagePadding,
            onItemSelected = { item -> onItemSelected(item, route.fetchAction) },
            onItemContextMenuSelected = onItemContextMenuSelected
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.SlimBrowseSubItemsNavEntry(
    serverConfig: State<ServerConfiguration?>,
    pagePadding: PaddingValues,
    appBarState: TopAppBarState?,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem, JiveAction) -> Job?,
    onItemContextMenuSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.SlimBrowseSubItemPage> { route ->
    val viewModel = koinViewModel<SlimBrowseSubItemListViewModel> {
        parametersOf(route.fetchAction, route.position)
    }
    val listState = rememberLazyListState()

    MainContentPage(
        appBarState = appBarState,
        indicatorState = listState.scrollIndicatorState
    ) {
        SlimBrowseSubItemList(
            state = listState,
            viewModel = viewModel,
            serverConfig = serverConfig.value,
            contentPadding = pagePadding,
            onItemSelected = { item -> onItemSelected(item, route.fetchAction) },
            onItemContextMenuSelected = onItemContextMenuSelected
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.GalleryNavEntry(
    serverConfig: State<ServerConfiguration?>,
    pagePadding: PaddingValues,
    appBarState: TopAppBarState?,
    onItemSelected: (SlideshowImage) -> Unit
) = entry<MainContentNavigation.GalleryPage> { route ->
    val gridState = rememberLazyStaggeredGridState()

    MainContentPage(
        appBarState = appBarState,
        indicatorState = gridState.scrollIndicatorState
    ) {
        GalleryGrid(
            state = gridState,
            items = route.items,
            serverConfig = serverConfig.value,
            contentPadding = pagePadding,
            onItemSelected = onItemSelected
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.LocalSearchResultsNavKey(
    serverConfig: State<ServerConfiguration?>,
    useGrid: State<Boolean>,
    gridCellMinSize: Dp,
    pagePadding: PaddingValues,
    appBarState: TopAppBarState?,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onItemContextMenuSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.LocalSearchResults> { route ->
    val viewModel = koinViewModel<LibrarySearchResultsViewModel> {
        parametersOf(route.searchTerm, route.mode)
    }
    val mode = rememberSlimBrowseItemListMode(useGrid.value, true, gridCellMinSize)

    MainContentPage(
        appBarState = appBarState,
        indicatorState = mode.scrollIndicatorState
    ) {
        LibrarySearchResultsItemList(
            viewModel = viewModel,
            serverConfig = serverConfig.value,
            mode = mode,
            contentPadding = pagePadding,
            onItemSelected = onItemSelected,
            onItemContextMenuSelected = onItemContextMenuSelected
        )
    }
}

@Composable
internal fun EntryProviderScope<NavKey>.RadioSearchResultsNavKey(
    serverConfig: State<ServerConfiguration?>,
    useGrid: State<Boolean>,
    gridCellMinSize: Dp,
    pagePadding: PaddingValues,
    appBarState: TopAppBarState?,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onItemContextMenuSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job?
) = entry<MainContentNavigation.RadioSearchResults> { route ->
    val viewModel = koinViewModel<RadioSearchResultsViewModel> {
        parametersOf(route.searchTerm)
    }
    val mode = rememberSlimBrowseItemListMode(useGrid.value, true, gridCellMinSize)

    MainContentPage(
        appBarState = appBarState,
        indicatorState = mode.scrollIndicatorState
    ) {
        RadioSearchResultsItemList(
            viewModel = viewModel,
            serverConfig = serverConfig.value,
            mode = mode,
            contentPadding = pagePadding,
            onItemSelected = onItemSelected,
            onItemContextMenuSelected = onItemContextMenuSelected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.HomeMenuInputNavEntry(
    onInputSubmitted: (title: String, action: JiveAction, isGoAction: Boolean) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.HomeMenuInput>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    InputBottomSheetContent(
        title = route.title,
        input = route.input,
        onInputSubmitted = { action ->
            onInputSubmitted(route.title, action, route.input.actionHasTarget)
                .also { it.invokeOnDoneOrNull { onDone(route) } }
        },
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
internal fun EntryProviderScope<NavKey>.HomeMenuTimePickerNavEntry(
    onInputSubmitted: (title: String, action: JiveAction, isGoAction: Boolean) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.HomeMenuTimePicker>(
    metadata = DialogSceneStrategy.dialog()
) { route ->
    InputTimePicker(
        title = route.title,
        input = route.input,
        onInputSubmitted = { action ->
            onInputSubmitted(route.title, action, route.input.actionHasTarget)
        },
        onClose = { onDone(route) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.SlimBrowseInputNavEntry(
    onInputSubmitted: (
        item: SlimBrowseItemList.SlimBrowseItem,
        action: JiveAction,
        isGoAction: Boolean
    ) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SlimBrowseInput>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    InputBottomSheetContent(
        title = route.item.title,
        input = route.input,
        onInputSubmitted = { action ->
            onInputSubmitted(route.item, action, route.input.actionHasTarget)
                .also { it.invokeOnDoneOrNull { onDone(route) } }
        }
    )
}

@Composable
internal fun EntryProviderScope<NavKey>.SlimBrowseTimePickerNavEntry(
    onInputSubmitted: (
        item: SlimBrowseItemList.SlimBrowseItem,
        action: JiveAction,
        isGoAction: Boolean
    ) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SlimBrowseTimePicker>(
    metadata = DialogSceneStrategy.dialog()
) { route ->
    InputTimePicker(
        title = route.item.title,
        input = route.input,
        onInputSubmitted = { action ->
            onInputSubmitted(route.item, action, route.input.actionHasTarget)
        },
        onClose = { onDone(route) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.SliderInputNavEntry(
    onValueChanged: (JiveAction) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SliderInput>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    SliderBottomSheetContent(
        title = route.title,
        slider = route.slider,
        onValueSet = { action ->
            onValueChanged(action)
                .also { it.invokeOnDoneOrNull { onDone(route) } }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.HomeMenuChoicesNavEntry(
    onChoiceSelected: (JiveAction) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.HomeMenuChoices>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    ChoicesBottomSheetContent(
        title = route.parentTitle,
        choices = route.choices,
        onChoiceSelected = { action ->
            onChoiceSelected(action)
                .also { it.invokeOnDoneOrNull { onDone(route) } }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.SlimBrowseChoicesNavEntry(
    onChoiceSelected: (JiveAction, SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.SlimBrowseChoices>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    ChoicesBottomSheetContent(
        title = route.item.title,
        choices = route.choices,
        onChoiceSelected = { action ->
            onChoiceSelected(action, route.item)
                .also { it.invokeOnDoneOrNull { onDone(route) } }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.InfoNavEntry() = entry<MainContentNavigation.Info>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    BottomSheetContentWrapper(
        title = route.title,
        busy = false
    ) {
        Text(
            text = AnnotatedString.fromHtml(route.info),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.ContextMenuNavEntry(
    serverConfig: State<ServerConfiguration?>,
    onItemSelected: (
        item: SlimBrowseItemList.SlimBrowseItem,
        parentItem: SlimBrowseItemList.SlimBrowseItem?
    ) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.ContextMenu>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    val viewModel = koinViewModel<ContextMenuViewModel> {
        parametersOf(route.menuItems, route.item)
    }

    ContextMenuBottomSheetContent(
        viewModel = viewModel,
        serverConfig = serverConfig.value,
        onItemSelected = { item ->
            onItemSelected(item, route.item)
                .also { it.invokeOnDoneOrNull { onDone(route) } }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryProviderScope<NavKey>.ItemActionsNavEntry(
    serverConfig: State<ServerConfiguration?>,
    onDownloadSelected: (DownloadRequestData) -> Unit,
    onActionSelected: (JiveAction, SlimBrowseItemList.SlimBrowseItem) -> Job?,
    onDone: (NavKey) -> Unit
) = entry<MainContentNavigation.ItemActions>(
    metadata = BottomSheetSceneStrategy.bottomSheet()
) { route ->
    val viewModel = koinViewModel<ItemActionsViewModel> { parametersOf(route.item) }

    ItemActionsMenuSheetContent(
        viewModel = viewModel,
        serverConfig = serverConfig.value,
        onActionSelected = { action ->
            when (action) {
                is ItemAction.Download -> {
                    onDownloadSelected(action.download)
                    null
                }

                is ItemAction.Action ->
                    onActionSelected(action.action, route.item)
                        .also { it.invokeOnDoneOrNull { onDone(route) } }
            }
        }
    )
}
