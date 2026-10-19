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

package de.maniac103.squeezeclient.ui.prefs

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.requestPermission
import de.maniac103.squeezeclient.ui.composables.DialogHost
import de.maniac103.squeezeclient.ui.theme.AppTheme
import org.koin.android.ext.android.inject

class SettingsActivity : ComponentActivity() {
    private val preferences: Preferences by inject()
    private val dialogHost = DialogHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(preferences.appTheme) {
                SettingsScreen(
                    preferences = preferences,
                    onGoBack = { finish() },
                    onCheckNotificationsPermissionForLocalPlayer = {
                        checkNotificationsPermissionForLocalPlayer()
                    }
                )
                dialogHost.Render()
            }
        }
    }

    private suspend fun checkNotificationsPermissionForLocalPlayer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return requestPermission(
            dialogHost = dialogHost,
            permission = Manifest.permission.POST_NOTIFICATIONS,
            rationaleDialogTextResId =
                R.string.local_player_notification_permission_rationale_message,
            rationaleDialogCancelButtonTextResId =
                R.string.local_player_notification_permission_rationale_cancel
        )
    }
}
