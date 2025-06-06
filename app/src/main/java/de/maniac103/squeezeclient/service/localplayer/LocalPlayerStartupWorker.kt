/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2025 Danny Baumann
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

package de.maniac103.squeezeclient.service.localplayer

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import de.maniac103.squeezeclient.extfuncs.localPlayerEnabled
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.workManager

class LocalPlayerStartupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val intent = Intent(applicationContext, LocalPlaybackService::class.java)
        applicationContext.startForegroundService(intent)
        return Result.success()
    }

    companion object {
        fun triggerStartOrStop(context: Context) {
            if (context.prefs.localPlayerEnabled) {
                val request = OneTimeWorkRequestBuilder<LocalPlayerStartupWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                context.workManager.enqueue(request)
            } else {
                val intent = Intent(context, LocalPlaybackService::class.java)
                context.stopService(intent)
            }
        }
    }
}
