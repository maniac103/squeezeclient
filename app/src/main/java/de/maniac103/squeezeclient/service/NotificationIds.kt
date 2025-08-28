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

package de.maniac103.squeezeclient.service

import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import de.maniac103.squeezeclient.R
import java.util.UUID

object NotificationIds {
    const val LOCAL_PLAYBACK = 1001
    const val MEDIA_CONTTROL_SERVICE = 1002
    fun forDownloadWork(workId: UUID) = workId.hashCode().or(0x80000000.toInt())

    data class ChannelInfo(
        val id: String,
        @param:StringRes val nameResId: Int,
        @param:StringRes val descResId: Int,
        val importance: Int
    )

    val CHANNEL_MEDIA_CONTROL = ChannelInfo(
        "media_control",
        R.string.media_notification_channel_name,
        // MediaSessionService doesn't allow specifying description
        0,
        NotificationManagerCompat.IMPORTANCE_DEFAULT
    )
    val CHANNEL_DOWNLOAD = ChannelInfo(
        "background_download",
        R.string.download_notification_channel_name,
        R.string.download_notification_channel_description,
        NotificationManagerCompat.IMPORTANCE_LOW
    )
    val CHANNEL_LOCAL_PLAYBACK = ChannelInfo(
        "local_playback",
        R.string.local_player_notification_channel_name,
        R.string.local_player_notification_channel_description,
        NotificationManagerCompat.IMPORTANCE_MIN
    )
}
