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

import android.content.res.Resources
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import de.maniac103.squeezeclient.service.NotificationIds

fun NotificationManagerCompat.getOrCreateNotificationChannel(
    res: Resources,
    info: NotificationIds.ChannelInfo
): NotificationChannelCompat {
    getNotificationChannelCompat(info.id)?.let { return it }

    return NotificationChannelCompat.Builder(info.id, info.importance)
        .apply {
            setName(res.getString(info.nameResId))
            setDescription(res.getString(info.descResId))
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setSound(null, null)
        }
        .build()
        .also { createNotificationChannel(it) }
}
