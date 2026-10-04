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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.serverMightCacheResults
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.max

data class BreadcrumbsItem(val title: String, val parentTitle: String?, val icon: ArtworkItem?)

class MainContentViewModel(
    private val application: Application,
    private val playerId: PlayerId
) : AndroidViewModel(application) {
    private val connectionHelper = application.connectionHelper
    private val navigationFlowInternal = MutableSharedFlow<MainContentNavigation>()
    private var pendingNavigation: Job? = null

    val initialPage get() = MainContentNavigation.JiveHomeMenuPage(playerId, "home", "")
    val navigationFlow = navigationFlowInternal.asSharedFlow()

    fun goToHome() = viewModelScope.launch {
        navigationFlowInternal.emit(MainContentNavigation.Home)
    }

    fun goBack(levels: Int) = viewModelScope.launch {
        navigationFlowInternal.emit(MainContentNavigation.GoBack(levels))
    }

    fun openLocalSearchResults(
        searchTerm: String,
        type: LibrarySearchRequest.Mode
    ) = viewModelScope.launch {
        navigationFlowInternal.emit(MainContentNavigation.Home)
        navigationFlowInternal.emit(
            MainContentNavigation.LocalSearchResults(playerId, searchTerm, type)
        )
    }

    fun openRadioSearchResults(searchTerm: String) = viewModelScope.launch {
        navigationFlowInternal.emit(MainContentNavigation.Home)
        navigationFlowInternal.emit(MainContentNavigation.RadioSearchResults(playerId, searchTerm))
    }

    fun onNowPlayingContextMenuAction(
        action: JiveAction,
        contextItem: SlimBrowseItemList.SlimBrowseItem,
        parentItem: SlimBrowseItemList.SlimBrowseItem
    ) = onHandleDoOrGoAction(action, true, contextItem, parentItem)

    fun onHomeItemSelected(item: JiveHomeMenuItem) = viewModelScope.launch {
        when {
            item.input != null -> {
                val route = when (item.input.type) {
                    JiveActions.Input.Type.Time ->
                        MainContentNavigation.HomeMenuTimePicker(item.title, item.input)

                    else -> MainContentNavigation.HomeMenuInput(item.title, item.input)
                }
                navigationFlowInternal.emit(route)
            }

            item.choices != null -> {
                navigationFlowInternal.emit(MainContentNavigation.HomeMenuChoices(item.title, item.choices))
            }

            item.doAction != null -> viewModelScope.launch {
                connectionHelper.executeAction(playerId, item.doAction)
            }

            item.goAction != null -> {
                handleGoAction(item.title, null, null, item.goAction)
            }

            else -> {
                navigationFlowInternal.emit(MainContentNavigation.JiveHomeMenuPage(playerId, item.id, item.title))
            }
        }
    }

    fun onHomeItemInputSubmitted(title: String, action: JiveAction, isGoAction: Boolean) =
        if (isGoAction) {
            handleGoAction(title, null, null, action)
        } else {
            viewModelScope.launch {
                connectionHelper.executeAction(playerId, action)
            }
        }

    fun onHomeItemChoiceSelected(choice: JiveAction) = viewModelScope.launch {
        connectionHelper.executeAction(playerId, choice)
    }

    fun onSlimBrowseItemSelected(item: SlimBrowseItemList.SlimBrowseItem, subItemFetchAction: JiveAction?) =
        item.actions?.let { actions ->
            when {
                actions.input != null -> viewModelScope.launch {
                    val route = when (actions.input.type) {
                        JiveActions.Input.Type.Time ->
                            MainContentNavigation.SlimBrowseTimePicker(item, actions.input)

                        else -> MainContentNavigation.SlimBrowseInput(item, actions.input)
                    }
                    navigationFlowInternal.emit(route)
                }

                actions.choices != null -> viewModelScope.launch {
                    navigationFlowInternal.emit(MainContentNavigation.SlimBrowseChoices(item, actions.choices))
                }

                actions.checkbox != null -> {
                    val action = if (actions.checkbox.state) {
                        actions.checkbox.offAction
                    } else {
                        actions.checkbox.onAction
                    }
                    onHandleDoOrGoAction(action, false, item, null)
                }

                actions.radio != null -> {
                    onHandleDoOrGoAction(actions.radio.action, false, item, null)
                }

                item.webLink != null -> viewModelScope.launch {
                    navigationFlowInternal.emit(MainContentNavigation.WebLink(item.title, item.webLink))
                }

                item.subItems != null -> subItemFetchAction?.let { fetchAction ->
                    viewModelScope.launch {
                        navigationFlowInternal.emit(
                            MainContentNavigation.SlimBrowseSubItemPage(
                                playerId,
                                item.title,
                                fetchAction,
                                item.listPosition
                            )
                        )
                    }
                }

                actions.goAction != null -> {
                    onHandleDoOrGoAction(actions.goAction, true, item, null)
                }

                else -> null
            }
        }

    fun onSlimBrowseContextMenu(item: SlimBrowseItemList.SlimBrowseItem) = item.actions?.let { actions ->
        when {
            actions.moreAction != null -> viewModelScope.launch {
                val itemList = loadContextMenuItems(actions.moreAction, actions)
                navigationFlowInternal.emit(MainContentNavigation.ContextMenu(playerId, item, itemList))
            }

            actions.hasContextMenu -> viewModelScope.launch {
                navigationFlowInternal.emit(MainContentNavigation.ItemActions(item))
            }

            else -> null
        }
    }

    fun onSlimBrowseInputSubmitted(
        item: SlimBrowseItemList.SlimBrowseItem,
        action: JiveAction,
        isGoAction: Boolean
    ) = onHandleDoOrGoAction(action, isGoAction, item, null)

    fun onSlimBrowseChoiceSelected(choice: JiveAction, item: SlimBrowseItemList.SlimBrowseItem) =
        onHandleDoOrGoAction(choice, false, item, null)

    fun onSliderValueChanged(action: JiveAction) = viewModelScope.launch {
        connectionHelper.executeAction(playerId, action)
    }

    fun extractDownloadDataForContextMenuItem(item: SlimBrowseItemList.SlimBrowseItem) =
        item.actions?.downloadData?.takeIf { item.tag == "download" }

    fun onContextMenuItemSelected(
        item: SlimBrowseItemList.SlimBrowseItem,
        parentItem: SlimBrowseItemList.SlimBrowseItem?
    ) = item.actions?.let { actions ->
        when {
            actions.doAction != null ->
                onHandleDoOrGoAction(actions.doAction, false, item, parentItem)

            actions.goAction != null ->
                onHandleDoOrGoAction(actions.goAction, true, item, parentItem)

            else -> null
        }
    }

    fun onItemActionSelected(action: JiveAction, item: SlimBrowseItemList.SlimBrowseItem) =
        onHandleDoOrGoAction(action, false, item, null)

    private suspend fun loadContextMenuItems(
        action: JiveAction,
        actions: JiveActions
    ): List<SlimBrowseItemList.SlimBrowseItem> {
        val loadedItems = connectionHelper.fetchItemsForAction(
            playerId,
            action,
            PagingParams.All,
            false
        )
        return if (actions.downloadData == null) {
            loadedItems.items
        } else {
            loadedItems.items.toMutableList().apply {
                // Create a fake slimbrowse item for the download, which has some constraints:
                // - Must contain a go action (otherwise it's assumed to be non-clickable)
                // - Go action must not point to a context menu
                // - Download data must match that of the base item
                // - Tag must match the expectation of onContextItemSelected
                val item = SlimBrowseItemList.SlimBrowseItem(
                    listPosition = size,
                    title = application.getString(R.string.action_download),
                    subText = null,
                    extraInfo = null,
                    textKey = null,
                    type = null,
                    trackType = null,
                    icon = null,
                    iconId = null,
                    actions = JiveActions(
                        goAction = JiveAction(emptyList(), emptyMap(), null, null),
                        doAction = null,
                        moreAction = null,
                        playAction = null,
                        addAction = null,
                        insertAction = null,
                        downloadData = actions.downloadData,
                        checkbox = null,
                        choices = null,
                        radio = null,
                        input = null,
                        slider = null,
                        onClickRefresh = null
                    ),
                    nextWindow = null,
                    subItems = null,
                    webLink = null,
                    tag = "download"
                )
                add(item)
            }
        }
    }

    private fun onHandleDoOrGoAction(
        action: JiveAction,
        isGoAction: Boolean,
        item: SlimBrowseItemList.SlimBrowseItem,
        parentItem: SlimBrowseItemList.SlimBrowseItem?
    ): Job? {
        // The logic below both translates nextWindow to adjust for whether it came from a
        // context menu or not, and splits navigation and refresh which are combined in the
        // protocol. Since it's a little hard to understand, here are the translation tables:
        //
        // nextWindow           without parent item         with parent item
        // Home                 keep                        keep
        // Parent               keep (go up 1 level)        discard (navigate to parent of menu)
        // GrandParent          keep (go up 2 levels)       adjust (go up 1 level)
        // NowPlaying           keep                        keep
        // RefreshSelf          keep (refresh 1 level)      discard (no need to refresh menu)
        // MyMusic              keep                        keep
        // ParentWithRefresh    keep (go up 1, refresh 2)   adjust (refresh 1 level)
        // Presets              keep                        keep
        //
        // refresh              without parent item         with parent item
        // RefreshSelf          1 level                     ---
        // RefreshParent        2 levels                    1 level
        // RefreshGrandParent   3 levels                    2 levels
        val nextWindow = action.nextWindow ?: item.nextWindow
        val actualNextWindow = when (nextWindow) {
            SlimBrowseItemList.NextWindow.Parent if parentItem != null ->
                null

            SlimBrowseItemList.NextWindow.GrandParent if parentItem != null ->
                SlimBrowseItemList.NextWindow.Parent

            SlimBrowseItemList.NextWindow.ParentWithRefresh ->
                if (parentItem != null) null else SlimBrowseItemList.NextWindow.Parent

            else -> nextWindow
        }
        val refreshLevelsFromNextWindow = when (nextWindow) {
            SlimBrowseItemList.NextWindow.RefreshSelf if parentItem == null ->
                1

            SlimBrowseItemList.NextWindow.ParentWithRefresh ->
                if (parentItem != null) 1 else 2

            else -> 0
        }
        val refreshLevelsFromRefresh = when (item.actions?.onClickRefresh) {
            JiveActions.RefreshBehavior.RefreshSelf ->
                if (parentItem != null) 0 else 1

            JiveActions.RefreshBehavior.RefreshParent ->
                if (parentItem != null) 1 else 2

            JiveActions.RefreshBehavior.RefreshGrandParent ->
                if (parentItem != null) 2 else 3

            null ->
                // fall back to refreshing the page for do actions without next window, since
                // choices and checkboxes usually do not have that
                if (isGoAction) 0 else 1
        }
        val refreshLevels = max(refreshLevelsFromNextWindow, refreshLevelsFromRefresh)

        return if (isGoAction && nextWindow == null) {
            handleGoAction(item.title, item, parentItem, action)
        } else {
            viewModelScope.launch {
                connectionHelper.executeAction(playerId, action)
                val navigation = when (actualNextWindow) {
                    SlimBrowseItemList.NextWindow.Parent -> MainContentNavigation.GoBack(1)

                    SlimBrowseItemList.NextWindow.GrandParent -> MainContentNavigation.GoBack(2)

                    SlimBrowseItemList.NextWindow.Home,
                    SlimBrowseItemList.NextWindow.MyMusic -> MainContentNavigation.Home

                    SlimBrowseItemList.NextWindow.NowPlaying -> MainContentNavigation.NowPlaying

                    else -> null
                }
                navigation?.let { navigationFlowInternal.emit(it) }
                refreshLevels
                    .takeIf { it > 0 }
                    ?.let { navigationFlowInternal.emit(MainContentNavigation.RefreshContent(it)) }
            }
        }
    }

    private fun handleGoAction(
        title: String,
        item: SlimBrowseItemList.SlimBrowseItem?,
        parentItem: SlimBrowseItemList.SlimBrowseItem?,
        action: JiveAction
    ): Job? {
        pendingNavigation?.cancel()
        pendingNavigation = viewModelScope.launch {
            if (action.isSlideshow) {
                val items = connectionHelper.fetchSlideshowImages(playerId, action)
                navigationFlowInternal.emit(MainContentNavigation.GalleryPage(items, title, parentItem?.title))
                return@launch
            }
            // Fetch first item of page to check whether we're dealing with a slider or a
            // normal page. Caveat here is that some actions (most notably artist info) cache
            // responses, so we need to make sure to fetch all entries for those as otherwise
            // the paged response might be incomplete later.
            // (see https://github.com/LMS-Community/slimserver/issues/1298)
            val page = if (action.serverMightCacheResults()) {
                PagingParams.All
            } else {
                PagingParams(0, 1)
            }
            val result = connectionHelper.fetchItemsForAction(playerId, action, page, false)
            val firstItem = result.items.getOrNull(0)
            val newRoute = when {
                result.totalCount == 1 && firstItem?.actions?.slider != null -> {
                    MainContentNavigation.SliderInput(title, firstItem.actions.slider)
                }

                result.totalCount == 0 && result.window?.textArea != null -> {
                    MainContentNavigation.Info(result.title ?: title, result.window.textArea)
                }

                else -> {
                    MainContentNavigation.SlimBrowseItemPage(
                        playerId,
                        title,
                        parentItem?.title,
                        parentItem ?: item,
                        action,
                        result.window?.windowStyle
                    )
                }
            }
            navigationFlowInternal.emit(newRoute)
        }
        return pendingNavigation
    }
}