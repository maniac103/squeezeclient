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

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.view.animation.Interpolator
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import de.maniac103.squeezeclient.SqueezeClientApplication
import de.maniac103.squeezeclient.model.ArtworkItem

val Context.connectionHelper get() =
    (applicationContext as SqueezeClientApplication).connectionHelper
val Context.jsonParser get() =
    (applicationContext as SqueezeClientApplication).json
val Context.httpClient get() =
    (applicationContext as SqueezeClientApplication).httpClient
val Context.workManager get() = WorkManager.getInstance(this)
val Context.prefs: SharedPreferences get() =
    PreferenceManager.getDefaultSharedPreferences(this)
val Context.backProgressInterpolator: Interpolator? get() =
    (applicationContext as SqueezeClientApplication).backProgressInterpolator

fun Context.imageCacheContains(item: ArtworkItem?) = item?.extractIconUrl(this)
    ?.let { MemoryCache.Key(it) }
    ?.let { imageLoader.memoryCache?.keys?.contains(it) == true }

suspend fun Context.loadArtwork(item: ArtworkItem?, size: Int): Drawable? {
    val request = ImageRequest.Builder(this)
        .data(item?.extractIconUrl(this))
        .size(size)
        .addServerCredentialsIfNeeded(this)
        .build()
    return imageLoader.execute(request)
        .drawable
        ?.withRoundedCorners(this)
}
