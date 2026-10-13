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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.markodevcic.peko.PermissionRequester
import com.markodevcic.peko.PermissionResult
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.await
import de.maniac103.squeezeclient.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject

class SettingsActivity : ComponentActivity() {
    private val preferences: Preferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
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
            }
        }
    }

    private suspend fun checkNotificationsPermissionForLocalPlayer(): Boolean {
        val requester = PermissionRequester.instance()

        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                // Don't need runtime permission in that case
                true

            requester.isAnyGranted(android.Manifest.permission.POST_NOTIFICATIONS) ->
                // Permission already granted
                true

            else ->
                requestNotificationPermissionForLocalPlayer()
        }
    }

    @SuppressLint("InlinedApi")
    private suspend fun requestNotificationPermissionForLocalPlayer(): Boolean {
        val requester = PermissionRequester.instance()
        val result = requester.request(android.Manifest.permission.POST_NOTIFICATIONS).first()
        when (result) {
            is PermissionResult.Denied.NeedsRationale -> {}
            is PermissionResult.Denied.DeniedPermanently -> {}
            is PermissionResult.Cancelled -> return false
            is PermissionResult.Granted -> return true
        }
        // When we're here, we need to show a rationale dialog
        val dialogResult = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.local_player_notification_permission_rationale_message)
            .create()
            .await(
                positiveText = getString(R.string.permission_rationale_allow),
                negativeText = getString(
                    R.string.local_player_notification_permission_rationale_cancel
                )
            )
        if (!dialogResult) {
            return false
        }
        if (result is PermissionResult.Denied.DeniedPermanently) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
            return false
        }
        return requester.request(android.Manifest.permission.POST_NOTIFICATIONS)
            .first() is PermissionResult.Granted
    }
}
