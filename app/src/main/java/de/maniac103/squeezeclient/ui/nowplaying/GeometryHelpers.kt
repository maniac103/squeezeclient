/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

@Immutable
internal data class Bounds(val start: Dp, val top: Dp, val width: Dp, val height: Dp) {
    val end get() = start + width
    val bottom get() = top + height
    val center get() = DpOffset(start + width / 2, top + height / 2)
}

@Stable
internal interface Morph<out T> {
    fun at(progress: Float): T
}

@Immutable
internal data class Interpolated<T>(val easing: Easing, val interpolator: (fraction: Float) -> T) :
    Morph<T> {
    override fun at(progress: Float): T = interpolator(easing.transform(progress))
}

@Immutable
internal data class InterpolatedBounds(
    val start: Bounds,
    val end: Bounds,
    val startEasing: Easing,
    val topEasing: Easing,
    val widthEasing: Easing,
    val heightEasing: Easing
) : Morph<Bounds> {
    override fun at(progress: Float) = Bounds(
        start = lerp(start.start, end.start, startEasing.transform(progress)),
        top = lerp(start.top, end.top, topEasing.transform(progress)),
        width = lerp(start.width, end.width, widthEasing.transform(progress)),
        height = lerp(start.height, end.height, heightEasing.transform(progress))
    )
}

@Immutable
internal data class Constant<T>(val value: T) : Morph<T> {
    override fun at(progress: Float): T = value
}

@Immutable
internal data class WindowEasing(
    val start: Float,
    val end: Float,
    val inner: Easing = LinearEasing
) : Easing {
    override fun transform(fraction: Float) = when {
        fraction <= start -> 0f
        fraction >= end -> 1f
        else -> inner.transform((fraction - start) / (end - start))
    }
}

internal fun morph(
    start: Bounds,
    end: Bounds,
    startEasing: Easing = LinearEasing,
    topEasing: Easing = LinearEasing,
    widthEasing: Easing = LinearEasing,
    heightEasing: Easing = LinearEasing
): Morph<Bounds> = InterpolatedBounds(start, end, startEasing, topEasing, widthEasing, heightEasing)

internal fun morph(start: Bounds, end: Bounds, easing: Easing) =
    InterpolatedBounds(start, end, easing, easing, easing, easing)

internal fun morph(start: Dp, end: Dp, easing: Easing = LinearEasing): Morph<Dp> =
    Interpolated(easing) { fraction -> lerp(start, end, fraction) }

internal fun morph(start: TextUnit, end: TextUnit, easing: Easing = LinearEasing): Morph<TextUnit> =
    Interpolated(easing) { fraction -> lerp(start, end, fraction) }

internal fun morph(start: Float, end: Float, easing: Easing = LinearEasing): Morph<Float> =
    Interpolated(easing) { fraction -> lerp(start, end, fraction) }

internal fun <T> constant(value: T): Morph<T> = Constant(value)

@Composable
internal fun Morph<Float>.rememberIsVisible(
    progress: MotionProgress,
    threshold: Float = 0.01f
): State<Boolean> = remember(this, progress) {
    derivedStateOf { at(progress.value) > threshold }
}
