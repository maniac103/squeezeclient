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

import androidx.lifecycle.ViewModel
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class JiveHomeItemListViewModel(
    connectionHelper: ConnectionHelper,
    playerId: PlayerId,
    val nodeId: String
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val homeMenuFlow = connectionHelper
        .playerState(playerId)
        .flatMapLatest { it.homeMenu }

    val homeMenuItemsFlow = homeMenuFlow.map { menu ->
        menu.values
            .filter { it.node == nodeId }
            .sortedBy { it.sortWeight }
            .map { mapItem(it) }
    }

    private fun mapItem(item: JiveHomeMenuItem): HomeListItem {
        val iconResourceId = ICON_MAPPING[item.id]
        val choiceLabel = item.choices?.let { it.items[it.selectedIndex].title }
        return HomeListItem(item.title, item.subText, iconResourceId, choiceLabel, item)
    }

    data class HomeListItem(
        val title: String,
        val subText: String?,
        val iconResourceId: Int?,
        val choiceLabel: String?,
        val source: JiveHomeMenuItem
    )

    companion object {
        private val ICON_MAPPING = mapOf(
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
    }
}
