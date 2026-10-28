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

package de.maniac103.squeezeclient.extfuncs

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.ui.composables.DialogHost
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun ComponentActivity.enableEdgeToEdge() {
    enableEdgeToEdge(
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

private suspend fun ComponentActivity.requestPermission(permission: String): Boolean =
    suspendCancellableCoroutine { cont ->
        val launcher = activityResultRegistry.register(
            "perm-$permission-${UUID.randomUUID()}",
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (cont.isActive) cont.resume(granted)
        }
        cont.invokeOnCancellation { launcher.unregister() }
        launcher.launch(permission)
    }

suspend fun ComponentActivity.requestPermission(
    dialogHost: DialogHost,
    permission: String,
    rationaleDialogTextResId: Int,
    rationaleDialogCancelButtonTextResId: Int
): Boolean {
    if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
        return true
    }

    if (requestPermission(permission)) {
        return true
    }

    val permanentlyDenied = !shouldShowRequestPermissionRationale(permission)

    // When we're here, we need to show a rationale dialog
    val accepted = dialogHost.confirm(
        title = getString(R.string.permission_rationale_title),
        message = getString(rationaleDialogTextResId),
        positiveText = getString(R.string.permission_rationale_allow),
        negativeText = getString(rationaleDialogCancelButtonTextResId)
    )
    if (!accepted) {
        return false
    }

    if (permanentlyDenied) {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
        return false
    }

    return requestPermission(permission)
}
