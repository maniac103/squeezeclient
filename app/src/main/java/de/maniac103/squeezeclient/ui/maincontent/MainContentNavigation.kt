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

import androidx.navigation3.runtime.NavKey
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.ParcelableArtworkItem
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.model.WindowStyle
import de.maniac103.squeezeclient.ui.composables.TextResource
import de.maniac103.squeezeclient.ui.composables.toTextResource
import kotlinx.serialization.Serializable

@Serializable
sealed class MainContentNavigation : NavKey {
    open fun generateBreadcrumb(): BreadcrumbsItem? = null

    @Serializable
    data class JiveHomeMenuPage(val nodeId: String, val title: String) : MainContentNavigation() {
        override fun generateBreadcrumb() = BreadcrumbsItem(title.toTextResource(), null, null)
    }

    @Serializable
    data class SlimBrowseItemPage(
        val title: String,
        val parentTitle: String?,
        val icon: ParcelableArtworkItem?,
        val fetchAction: JiveAction,
        val windowStyle: WindowStyle?
    ) : MainContentNavigation() {
        override fun generateBreadcrumb() = BreadcrumbsItem(
            title.toTextResource(),
            parentTitle?.toTextResource(),
            icon?.takeIf { it.isValidIcon }
        )
    }

    @Serializable
    data class SlimBrowseSubItemPage(
        val title: String,
        val fetchAction: JiveAction,
        val position: Int
    ) : MainContentNavigation() {
        override fun generateBreadcrumb() = BreadcrumbsItem(title.toTextResource(), null, null)
    }

    @Serializable
    data class GalleryPage(
        val items: List<SlideshowImage>,
        val title: String,
        val parentTitle: String?
    ) : MainContentNavigation() {
        override fun generateBreadcrumb() =
            BreadcrumbsItem(title.toTextResource(), parentTitle?.toTextResource(), null)
    }

    @Serializable
    data class LocalSearchResults(val searchTerm: String, val mode: LibrarySearchRequest.Mode) :
        MainContentNavigation() {
        override fun generateBreadcrumb() = BreadcrumbsItem(
            TextResource.StringRes(R.string.page_title_library_search, searchTerm),
            null,
            null
        )
    }

    @Serializable
    data class RadioSearchResults(val searchTerm: String) : MainContentNavigation() {
        override fun generateBreadcrumb() = BreadcrumbsItem(
            TextResource.StringRes(R.string.page_title_radio_search, searchTerm),
            null,
            null
        )
    }

    @Serializable
    data class HomeMenuInput(val title: String, val input: JiveActions.Input) :
        MainContentNavigation()

    @Serializable
    data class HomeMenuTimePicker(val title: String, val input: JiveActions.Input) :
        MainContentNavigation()

    @Serializable
    data class SlimBrowseInput(
        val item: SlimBrowseItemList.SlimBrowseItem,
        val input: JiveActions.Input
    ) : MainContentNavigation()

    @Serializable
    data class SlimBrowseTimePicker(
        val item: SlimBrowseItemList.SlimBrowseItem,
        val input: JiveActions.Input
    ) : MainContentNavigation()

    @Serializable
    data class SliderInput(val title: String, val slider: JiveActions.Slider) :
        MainContentNavigation()

    @Serializable
    data class HomeMenuChoices(val parentTitle: String, val choices: JiveActions.Choices) :
        MainContentNavigation()

    @Serializable
    data class SlimBrowseChoices(
        val item: SlimBrowseItemList.SlimBrowseItem,
        val choices: JiveActions.Choices
    ) : MainContentNavigation()

    @Serializable
    data class Info(val title: String, val info: String) : MainContentNavigation()

    @Serializable
    data class ContextMenu(
        val item: SlimBrowseItemList.SlimBrowseItem,
        val menuItems: List<SlimBrowseItemList.SlimBrowseItem>
    ) : MainContentNavigation()

    @Serializable
    data class ItemActions(val item: SlimBrowseItemList.SlimBrowseItem) : MainContentNavigation()

    @Serializable
    data class WebLink(val title: String, val link: String) : MainContentNavigation()

    @Serializable
    object Home : MainContentNavigation()

    @Serializable
    object NowPlaying : MainContentNavigation()

    @Serializable
    data class GoBack(val levels: Int) : MainContentNavigation()

    @Serializable
    data class RefreshContent(val levels: Int) : MainContentNavigation()
}
