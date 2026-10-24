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

package de.maniac103.squeezeclient.ui.itemlist

import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.SlimBrowseItemList

sealed interface JiveUiItemTrailingWidget {
    data class Choice(val label: String) : JiveUiItemTrailingWidget
    data class Checkbox(val checked: Boolean) : JiveUiItemTrailingWidget
    data class Radio(val checked: Boolean) : JiveUiItemTrailingWidget
    data object ContextMenu : JiveUiItemTrailingWidget
}

sealed interface JiveUiItemIcon {
    data object Spacer : JiveUiItemIcon
    data class Icon(val iconResId: Int) : JiveUiItemIcon
    data class Artwork(val artwork: ArtworkItem) : JiveUiItemIcon
}

data class JiveUiItem(
    val title: String,
    val subText: String?,
    val icon: JiveUiItemIcon?,
    val trailingWidget: JiveUiItemTrailingWidget?
)

data class JiveHomeMenuUiItem(val uiItem: JiveUiItem, val source: JiveHomeMenuItem)

data class SlimBrowseUiItem(val uiItem: JiveUiItem, val source: SlimBrowseItemList.SlimBrowseItem)

private val JIVE_HOME_ICON_MAPPING = mapOf(
    "advancedSettings" to R.drawable.hm_advancedsettings,
    "extras" to R.drawable.hm_advancedsettings,
    "favorites" to R.drawable.hm_favorites,
    "globalSearch" to R.drawable.hm_search,
    "homeSearchRecent" to R.drawable.hm_recentsearch,
    "myMusic" to R.drawable.hm_mymusic,
    "myMusicAlbums" to R.drawable.hm_albums,
    "myMusicAlbumsVariousArtists" to R.drawable.hm_compilations,
    "myMusicArtists" to R.drawable.hm_artists,
    "myMusicArtistsAlbumArtists" to R.drawable.hm_albumartists,
    "myMusicArtistsAllArtists" to R.drawable.hm_artists,
    "myMusicArtistsComposers" to R.drawable.hm_composers,
    "myMusicGenres" to R.drawable.hm_genres,
    "myMusicMusicFolder" to R.drawable.hm_musicfolder,
    "myMusicFileSystem" to R.drawable.hm_folder,
    "myMusicNewMusic" to R.drawable.hm_newmusic,
    "myMusicPlaylists" to R.drawable.hm_playlists,
    "myMusicSearch" to R.drawable.hm_search,
    "myMusicSearchRecent" to R.drawable.hm_recentsearch,
    "myMusicWorks" to R.drawable.hm_works,
    "myMusicYears" to R.drawable.hm_years,
    "opmlappgallery" to R.drawable.hm_appgallery,
    "opmlmyapps" to R.drawable.hm_apps,
    "opmlselectRemoteLibrary" to R.drawable.hm_remotelibrary,
    "opmlselectVirtualLibrary" to R.drawable.hm_virtuallibrary,
    "playerpower" to R.drawable.hm_playerpower,
    "radios" to R.drawable.hm_radios,
    "randomplay" to R.drawable.hm_randomplay,
    "settings" to R.drawable.hm_settings,
    "settingsAlarm" to R.drawable.hm_alarm_settings,
    "settingsAudio" to R.drawable.hm_audio_settings,
    "settingsDontStopTheMusic" to R.drawable.hm_dontstopthemusic,
    "settingsPlayerNameChange" to R.drawable.hm_name_change,
    "settingsRepeat" to R.drawable.hm_repeat,
    "settingsShuffle" to R.drawable.hm_shuffle,
    "settingsSleep" to R.drawable.hm_sleep,
    "settingsSync" to R.drawable.hm_sync
)

internal fun JiveHomeMenuItem.toUiItem(): JiveHomeMenuUiItem {
    val iconResourceId = JIVE_HOME_ICON_MAPPING[id]
    val choiceLabel = choices?.let { it.items[it.selectedIndex].title }
    val uiItem = JiveUiItem(
        title = title,
        subText = subText,
        icon = iconResourceId
            ?.let { JiveUiItemIcon.Icon(it) }
            ?: JiveUiItemIcon.Spacer,
        trailingWidget = choiceLabel?.let { JiveUiItemTrailingWidget.Choice(it) }
    )
    return JiveHomeMenuUiItem(uiItem, this)
}

internal fun SlimBrowseItemList.SlimBrowseItem.toUiItem(): SlimBrowseUiItem {
    val trailingWidget = actions?.let { actions ->
        when {
            actions.checkbox != null ->
                JiveUiItemTrailingWidget.Checkbox(actions.checkbox.state)

            actions.radio != null ->
                JiveUiItemTrailingWidget.Radio(actions.radio.state)

            actions.choices != null ->
                JiveUiItemTrailingWidget.Choice(
                    actions.choices.items[actions.choices.selectedIndex].title
                )

            actions.hasContextMenu -> JiveUiItemTrailingWidget.ContextMenu

            else -> null
        }
    }

    val uiItem = JiveUiItem(
        title = title,
        subText = subText ?: extraInfo,
        icon = JiveUiItemIcon.Artwork(this),
        trailingWidget = trailingWidget
    )
    return SlimBrowseUiItem(uiItem, this)
}
