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

package de.maniac103.squeezeclient.ui.volume

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class VolumeFragmentRootView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    @Suppress("unused") // used via animator
    fun setXOffsetRatio(offset: Float) {
        width.takeIf { it > 0 }?.let { translationX = offset * it }
    }
}
