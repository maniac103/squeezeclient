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

package de.maniac103.squeezeclient.ui.widget

import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.InsetDrawable

class RoundedCornerProgressDrawable @JvmOverloads constructor(
    drawable: Drawable? = null
) : InsetDrawable(drawable, 0) {

    companion object {
        private const val MAX_LEVEL = 10000 // Taken from Drawable
    }

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean {
        onLevelChange(level)
        return super.onLayoutDirectionChanged(layoutDirection)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        onLevelChange(level)
    }

    override fun onLevelChange(level: Int): Boolean {
        val db = drawable?.bounds!!
        // On 0, the width is bounds.height (a circle), and on MAX_LEVEL, the width is bounds.width
        val width = bounds.height() + (bounds.width() - bounds.height()) * level / MAX_LEVEL
        drawable?.setBounds(bounds.left, db.top, bounds.left + width, db.bottom)
        return super.onLevelChange(level)
    }

    override fun getConstantState(): ConstantState {
        // This should not be null as it was created with a state in the constructor.
        return RoundedCornerState(super.getConstantState()!!)
    }

    override fun getChangingConfigurations(): Int {
        return super.getChangingConfigurations() or ActivityInfo.CONFIG_DENSITY
    }

    override fun canApplyTheme(): Boolean {
        return (drawable?.canApplyTheme() ?: false) || super.canApplyTheme()
    }

    private class RoundedCornerState(private val wrappedState: ConstantState) : ConstantState() {
        override fun newDrawable(): Drawable {
            return newDrawable(null, null)
        }

        override fun newDrawable(res: Resources?, theme: Resources.Theme?): Drawable {
            val wrapper = wrappedState.newDrawable(res, theme) as DrawableWrapper
            return RoundedCornerProgressDrawable(wrapper.drawable)
        }

        override fun getChangingConfigurations(): Int {
            return wrappedState.changingConfigurations
        }

        override fun canApplyTheme(): Boolean {
            return true
        }
    }
}
