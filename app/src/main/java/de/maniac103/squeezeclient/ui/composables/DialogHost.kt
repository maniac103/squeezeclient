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

package de.maniac103.squeezeclient.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred

@Stable
class DialogHost {
    private var pending by mutableStateOf<PendingDialog?>(null)

    class PendingDialog(
        val title: String,
        val message: String,
        val positiveText: String,
        val negativeText: String,
        val result: CompletableDeferred<Boolean>
    )

    suspend fun confirm(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = PendingDialog(title, message, positiveText, negativeText, deferred)
        return try {
            deferred.await()
        } finally {
            pending = null
        }
    }

    @Composable
    fun Render() {
        val dialog = pending
            ?: return

        AlertDialog(
            title = {
                Text(dialog.title)
            },
            text = {
                Text(dialog.message)
            },
            confirmButton = {
                TextButton(
                    onClick = { dialog.result.complete(true) }
                ) {
                    Text(dialog.positiveText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dialog.result.complete(false) }
                ) {
                    Text(dialog.negativeText)
                }
            },
            onDismissRequest = {
                dialog.result.complete(false)
            }
        )
    }
}
