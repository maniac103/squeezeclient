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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.target.ImageViewTarget
import kotlin.math.max

fun ImageRequest.Builder.withRoundedCorners(view: ImageView) =
    this.target(object : ImageViewTarget(view) {
        override var drawable: Drawable?
            get() = super.drawable
            set(value) {
                super.drawable = value?.withRoundedCorners(view.context)
            }
    })

fun Context.imageCacheContains(url: String) =
    imageLoader.memoryCache?.keys?.contains(MemoryCache.Key(url)) == true

suspend fun Context.loadImage(url: String, size: Int): Drawable? {
    val request = ImageRequest.Builder(this)
        .data(url)
        .size(size)
        .build()
    return imageLoader.execute(request)
        .drawable
        ?.withRoundedCorners(this)
}

fun Drawable.withRoundedCorners(context: Context) = if (this is BitmapDrawable) {
    val bitmapSize = max(bitmap.width, bitmap.height).toFloat()
    RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
        cornerRadius = bitmapSize / 10F
    }
} else {
    this
}
