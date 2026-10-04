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
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.ParcelableArtworkItem
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.model.WindowStyle
import kotlinx.serialization.Serializable

@Serializable
sealed class MainContentNavigation : NavKey {
    @Serializable
    open class JiveHomeMenuPage(val playerId: PlayerId, val nodeId: String, val title: String) : MainContentNavigation()

    @Serializable
    class SlimBrowseItemPage(
        val playerId: PlayerId,
        val title: String,
        val parentTitle: String?,
        val icon: ParcelableArtworkItem?,
        val fetchAction: JiveAction,
        val windowStyle: WindowStyle?
    ) : MainContentNavigation()

    @Serializable
    class SlimBrowseSubItemPage(val playerId: PlayerId, val title: String, val fetchAction: JiveAction, val position: Int) : MainContentNavigation()

    @Serializable
    class GalleryPage(val items: List<SlideshowImage>, val title: String, val parentTitle: String?) : MainContentNavigation()

    @Serializable
    class LocalSearchResults(val playerId: PlayerId, val searchTerm: String, val mode: LibrarySearchRequest.Mode) : MainContentNavigation()

    @Serializable
    class RadioSearchResults(val playerId: PlayerId, val searchTerm: String) : MainContentNavigation()

    @Serializable
    class HomeMenuInput(val title: String, val input: JiveActions.Input) : MainContentNavigation()

    @Serializable
    class HomeMenuTimePicker(val title: String, val input: JiveActions.Input) : MainContentNavigation()

    @Serializable
    class SlimBrowseInput(val item : SlimBrowseItemList.SlimBrowseItem, val input: JiveActions.Input) : MainContentNavigation()

    @Serializable
    class SlimBrowseTimePicker(val item : SlimBrowseItemList.SlimBrowseItem, val input: JiveActions.Input) : MainContentNavigation()

    @Serializable
    class SliderInput(val title: String, val slider: JiveActions.Slider) : MainContentNavigation()

    @Serializable
    class HomeMenuChoices(val parentTitle: String, val choices: JiveActions.Choices) : MainContentNavigation()

    @Serializable
    class SlimBrowseChoices(val item: SlimBrowseItemList.SlimBrowseItem, val choices: JiveActions.Choices) : MainContentNavigation()

    @Serializable
    class Info(val title: String, val info: String) : MainContentNavigation()

    @Serializable
    class ContextMenu(val playerId: PlayerId, val item: SlimBrowseItemList.SlimBrowseItem, val menuItems: List<SlimBrowseItemList.SlimBrowseItem>) : MainContentNavigation()

    @Serializable
    class ItemActions(val item: SlimBrowseItemList.SlimBrowseItem) : MainContentNavigation()

    @Serializable
    class WebLink(val title: String, val link: String) : MainContentNavigation()

    @Serializable
    object Home : MainContentNavigation()

    @Serializable
    object NowPlaying : MainContentNavigation()

    @Serializable
    class GoBack(val levels: Int) : MainContentNavigation()

    @Serializable
    class RefreshContent(val levels: Int) : MainContentNavigation()
}