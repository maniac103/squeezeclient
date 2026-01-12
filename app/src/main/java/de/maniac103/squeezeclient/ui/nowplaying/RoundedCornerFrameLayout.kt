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

package de.maniac103.squeezeclient.ui.nowplaying

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ColorStateListDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import de.maniac103.squeezeclient.R

class RoundedCornerFrameLayout(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs) {

    private val backgroundShape: MaterialShapeDrawable
    private val amountInterpolator = AnimationUtils.loadInterpolator(
        context,
        android.R.interpolator.decelerate_quint
    )

    private var cornerRadius = 0F
    private var corneredEdge = TOP_EDGE
    private var radiusAmount = 1F

    init {
        context.obtainStyledAttributes(attrs, R.styleable.RoundedCornerFrameLayout).use {
            cornerRadius = it.getDimension(R.styleable.RoundedCornerFrameLayout_cornerRadius, 0F)
            corneredEdge = it.getInteger(
                R.styleable.RoundedCornerFrameLayout_corneredEdge,
                TOP_EDGE
            )
        }

        backgroundShape = createBackgroundFromOldColor(background)
        background = backgroundShape
        applyRadius()
    }

    fun setCorneredEdge(edge: Int) {
        if (corneredEdge != edge) {
            corneredEdge = edge
            applyRadius()
        }
    }

    fun setRadiusAmount(amount: Float) {
        if (radiusAmount != amount) {
            radiusAmount = amount
            applyRadius()
        }
    }

    private fun applyRadius() {
        val startIsRight = layoutDirection == LAYOUT_DIRECTION_RTL
        val isLeftEdge = if (startIsRight) corneredEdge == END_EDGE else corneredEdge == START_EDGE
        val isRightEdge = if (startIsRight) corneredEdge == START_EDGE else corneredEdge == END_EDGE
        val topLeft = corneredEdge == TOP_EDGE || isLeftEdge
        val topRight = corneredEdge == TOP_EDGE || isRightEdge
        val bottomLeft = corneredEdge == BOTTOM_EDGE || isLeftEdge
        val bottomRight = corneredEdge == BOTTOM_EDGE || isRightEdge
        val radius = amountInterpolator.getInterpolation(radiusAmount) * cornerRadius

        backgroundShape.shapeAppearanceModel = ShapeAppearanceModel.Builder()
            .setTopLeftCornerSize(if (topLeft) radius else 0F)
            .setTopRightCornerSize(if (topRight) radius else 0F)
            .setBottomLeftCornerSize(if (bottomLeft) radius else 0F)
            .setBottomRightCornerSize(if (bottomRight) radius else 0F)
            .build()
    }

    private fun createBackgroundFromOldColor(origBg: Drawable?): MaterialShapeDrawable {
        if (origBg is MaterialShapeDrawable) {
            return origBg
        }
        val materialShapeDrawable = MaterialShapeDrawable()

        val originalBackgroundColor = when {
            origBg is ColorDrawable ->
                ColorStateList.valueOf(origBg.color)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && origBg is ColorStateListDrawable ->
                origBg.colorStateList

            else -> null
        }

        originalBackgroundColor?.let { materialShapeDrawable.fillColor = it }
        return materialShapeDrawable
    }

    companion object {
        const val TOP_EDGE = 0
        const val BOTTOM_EDGE = 1
        const val START_EDGE = 2
        const val END_EDGE = 3
    }
}
