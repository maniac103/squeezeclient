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

package de.maniac103.squeezeclient.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.google.android.material.shape.MaterialShapeDrawable

class MainContentContainerView(context: Context, attrs: AttributeSet) :
    LinearLayout(context, attrs) {

    private val backgroundDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
    private var pendingXOffset: Float? = null

    fun enableMainContentBackground() {
        background = backgroundDrawable
    }

    @Suppress("unused") // used via animator
    fun setCornerRadius(radius: Float) {
        backgroundDrawable.setCornerSize(radius)
    }

    @Suppress("unused") // used via animator
    fun setXOffsetRatio(offset: Float) {
        pendingXOffset = offset
        applyPendingXOffsetIfPossible()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) {
            applyPendingXOffsetIfPossible()
        }
    }

    private fun applyPendingXOffsetIfPossible() {
        pendingXOffset.takeIf { width > 0 }?.let {
            translationX = it * width
            pendingXOffset = null
        }
    }
}