/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.extfuncs

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import coil.request.ImageRequest
import coil.target.ImageViewTarget
import kotlin.math.max

fun ImageRequest.Builder.withRoundedCorners(view: ImageView) =
    this.target(object : ImageViewTarget(view) {
        override var drawable: Drawable?
            get() = super.drawable
            set(value) {
                super.drawable = if (value is BitmapDrawable) {
                    val bitmap = value.bitmap
                    RoundedBitmapDrawableFactory.create(view.context.resources, bitmap).apply {
                        val bitmapSize = max(bitmap.width, bitmap.height).toFloat()
                        cornerRadius = bitmapSize / 10F
                    }
                } else {
                    value
                }
            }
    })
