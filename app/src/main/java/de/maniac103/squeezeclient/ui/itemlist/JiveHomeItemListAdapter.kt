/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.ListItemSlimbrowseBinding
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.ui.common.BasePrepopulatedListAdapter

class JiveHomeItemListAdapter(items: List<JiveHomeMenuItem> = listOf()) :
    BasePrepopulatedListAdapter<
        JiveHomeMenuItem,
        JiveHomeItemListAdapter.JiveHomeItemListViewHolder
        >(items) {
    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): JiveHomeItemListViewHolder {
        val binding = ListItemSlimbrowseBinding.inflate(inflater, parent, false)
        val contextLayoutResource = when (viewType) {
            VIEW_TYPE_CHOICES -> R.layout.list_item_extension_choices
            else -> null
        }
        contextLayoutResource?.let { inflater.inflate(it, binding.contextContainer) }

        return JiveHomeItemListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JiveHomeItemListViewHolder, item: JiveHomeMenuItem) {
        holder.bind(item)
    }

    override fun onHolderBusyStateChanged(holder: JiveHomeItemListViewHolder, busy: Boolean) {
        holder.updateBusyState(busy)
    }

    override fun getItemViewType(item: JiveHomeMenuItem): Int = when {
        item.choices != null -> VIEW_TYPE_CHOICES
        else -> VIEW_TYPE_NORMAL
    }

    class JiveHomeItemListViewHolder(private val binding: ListItemSlimbrowseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val choiceLabel = itemView.findViewById<TextView>(R.id.choice_label)

        fun bind(item: JiveHomeMenuItem) {
            binding.title.text = item.title
            binding.subtext.text = item.subText
            binding.subtext.isVisible = !item.subText.isNullOrEmpty()
            item.choices?.let { choiceLabel.text = it.items[it.selectedIndex].title }

            val iconResId = ICON_MAPPING[item.id]
            when {
                iconResId != null -> binding.icon.setImageResource(iconResId)
                BuildConfig.DEBUG -> binding.icon.setImageDrawable(ColorDrawable(Color.RED))
                else -> binding.icon.setImageDrawable(null)
            }
        }

        fun updateBusyState(busy: Boolean) {
            binding.contextContainer.forEach { child ->
                val isBusyIndicator = child == binding.loadingIndicator
                child.isVisible = busy == isBusyIndicator
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_CHOICES = 1

        private val ICON_MAPPING = mapOf(
            "advancedSettings" to R.drawable.hm_advancedsettings,
            "extras" to R.drawable.hm_advancedsettings,
            "favorites" to R.drawable.hm_favorites,
            "globalSearch" to R.drawable.hm_search,
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
