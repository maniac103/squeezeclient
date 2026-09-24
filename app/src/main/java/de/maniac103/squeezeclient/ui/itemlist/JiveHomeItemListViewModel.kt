package de.maniac103.squeezeclient.ui.itemlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PlayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class JiveHomeItemListViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val playerId = savedStateHandle.get<PlayerId>("playerId")
        ?: throw IllegalArgumentException("Missing player ID")
    private val nodeId = savedStateHandle.get<String>("nodeId")
        ?: throw IllegalArgumentException("Missing node ID")

    @OptIn(ExperimentalCoroutinesApi::class)
    val homeMenuFlow = application.connectionHelper
        .playerState(playerId)
        .flatMapLatest { it.homeMenu }

    val homeMenuItemsFlow = homeMenuFlow.map { menu ->
        menu.values
            .filter { it.node == nodeId }
            .sortedBy { it.sortWeight }
            .map { mapItem(it) }
    }

    private fun mapItem(item: JiveHomeMenuItem) : HomeListItem {
        val iconResourceId = ICON_MAPPING[item.id]
        return HomeListItem(item.title, item.subText, iconResourceId, item)
    }

    data class HomeListItem(
        val title: String,
        val subText: String?,
        val iconResourceId: Int?,
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
            "myMusicNewMusic" to R.drawable.hm_newmusic,
            "myMusicPlaylists" to R.drawable.hm_playlists,
            "myMusicSearch" to R.drawable.hm_search,
            "myMusicSearchRecent" to R.drawable.hm_recentsearch,
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