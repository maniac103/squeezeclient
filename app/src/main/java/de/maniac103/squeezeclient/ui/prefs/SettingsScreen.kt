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

package de.maniac103.squeezeclient.ui.prefs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R

@Composable
fun SettingsScreen(
    preferences: Preferences,
    onGoBack: () -> Unit,
    onCheckNotificationsPermissionForLocalPlayer: suspend () -> Boolean
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            subtitle = { Text(stringResource(R.string.settings_title)) },
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            navigationIcon = {
                IconButton(
                    onClick = onGoBack
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_left_24dp),
                        contentDescription = null // FIXME
                    )
                }
            }
        )
    }
) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .nestedScroll(rememberNestedScrollInteropConnection())
    ) {
        PreferenceGroup(
            title = stringResource(R.string.settings_category_display)
        ) {
            entry { position ->
                val mapLabelResId = { value: Preferences.AppThemeMode ->
                    when (value) {
                        Preferences.AppThemeMode.Light ->
                            R.string.settings_theme_light

                        Preferences.AppThemeMode.Dark ->
                            R.string.settings_theme_dark

                        Preferences.AppThemeMode.SystemDefault ->
                            R.string.settings_theme_follow_system
                    }
                }

                ListPreference(
                    position = position,
                    title = stringResource(R.string.settings_theme_title),
                    labelProvider = { value -> stringResource(mapLabelResId(value)) },
                    summaryProvider = { value -> stringResource(mapLabelResId(value)) },
                    preference = preferences.appTheme
                )
            }

            entry { position ->
                SwitchPreference(
                    position = position,
                    title = stringResource(R.string.settings_force_grid_title),
                    summaryProvider = { enabled ->
                        val resId = when {
                            enabled -> R.string.settings_force_grid_summary_on
                            else -> R.string.settings_force_grid_summary_off
                        }
                        stringResource(resId)
                    },
                    preference = preferences.forceGridLayout
                )
            }
        }

        PreferenceGroup(
            title = stringResource(R.string.settings_category_playback)
        ) {
            entry { position ->
                SwitchPreference(
                    position = position,
                    title = stringResource(R.string.settings_volume_buttons_title),
                    summaryProvider = { _ ->
                        stringResource(R.string.settings_volume_buttons_summary)
                    },
                    preference = preferences.useVolumeButtonsForPlayerVolume
                )
            }

            entry { position ->
                SliderPreference(
                    position = position,
                    title = stringResource(R.string.settings_volume_step_size_title),
                    summaryProvider = { value ->
                        stringResource(R.string.settings_volume_step_size_summary, value)
                    },
                    minValue = 1,
                    maxValue = 10,
                    preference = preferences.volumeStepSize
                )
            }

            entry { position ->
                SliderPreference(
                    position = position,
                    title = stringResource(R.string.settings_fade_in_seconds_title),
                    summaryProvider = { value ->
                        when (value) {
                            0 -> stringResource(R.string.settings_fade_in_seconds_summary_disabled)

                            else -> pluralStringResource(
                                R.plurals.settings_fade_in_seconds_summary,
                                value,
                                value
                            )
                        }
                    },
                    minValue = 0,
                    maxValue = 10,
                    preference = preferences.fadeInDurationInSeconds
                )
            }
        }

        PreferenceGroup(
            title = stringResource(R.string.settings_category_download)
        ) {
            entry { position ->
                val mapLabelResId = { value: Preferences.DownloadFolderStructure ->
                    when (value) {
                        Preferences.DownloadFolderStructure.AsOnServer ->
                            R.string.settings_download_path_structure_entry_as_server

                        Preferences.DownloadFolderStructure.AlbumUnderArtist ->
                            R.string.settings_download_path_structure_entry_album_under_artist

                        Preferences.DownloadFolderStructure.ArtistAlbum ->
                            R.string.settings_download_path_structure_entry_artist_album

                        Preferences.DownloadFolderStructure.Album ->
                            R.string.settings_download_path_structure_entry_album

                        Preferences.DownloadFolderStructure.Artist ->
                            R.string.settings_download_path_structure_entry_artist
                    }
                }

                ListPreference(
                    position = position,
                    title = stringResource(R.string.settings_local_player_volume_mode_title),
                    labelProvider = { value -> stringResource(mapLabelResId(value)) },
                    summaryProvider = { value -> stringResource(mapLabelResId(value)) },
                    preference = preferences.downloadFolderStructure
                )
            }
        }

        PreferenceGroup(
            title = stringResource(R.string.settings_category_local_player)
        ) {
            entry { position ->
                // TODO: permission request
                SwitchPreference(
                    position = position,
                    title = stringResource(R.string.settings_local_player_enabled_title),
                    summaryProvider = { _ ->
                        stringResource(R.string.settings_local_player_enabled_summary)
                    },
                    onInterceptValueChange = { newValue ->
                        !newValue || onCheckNotificationsPermissionForLocalPlayer()
                    },
                    preference = preferences.localPlayerEnabled
                )
            }

            entry { position ->
                ListPreference(
                    position = position,
                    title = stringResource(R.string.settings_local_player_volume_mode_title),
                    labelProvider = { value ->
                        val resId = when (value) {
                            Preferences.LocalPlayerVolumeMode.PlayerOnly ->
                                R.string.settings_local_player_volume_mode_player_only

                            Preferences.LocalPlayerVolumeMode.Device ->
                                R.string.settings_local_player_volume_mode_device

                            Preferences.LocalPlayerVolumeMode.DeviceWhilePlaying ->
                                R.string.settings_local_player_volume_mode_device_while_playing
                        }
                        stringResource(resId)
                    },
                    summaryProvider = { value ->
                        val resId = when (value) {
                            Preferences.LocalPlayerVolumeMode.PlayerOnly ->
                                R.string.settings_local_player_volume_mode_summary_player_only

                            Preferences.LocalPlayerVolumeMode.Device ->
                                R.string.settings_local_player_volume_mode_summary_device

                            Preferences.LocalPlayerVolumeMode.DeviceWhilePlaying ->
                                R.string
                                    .settings_local_player_volume_mode_summary_device_while_playing
                        }
                        stringResource(resId)
                    },
                    preference = preferences.localPlayerVolumeMode
                )
            }
        }
    }
}
