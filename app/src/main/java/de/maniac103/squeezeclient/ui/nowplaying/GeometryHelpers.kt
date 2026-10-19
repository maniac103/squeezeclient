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

internal fun interface Interpolator<T> {
    fun interpolate(start: T, end: T, fraction: Float): T
}

@Immutable
internal data class Interpolated<T>(
    val start: T,
    val end: T,
    val interpolator: Interpolator<T>,
    val easing: Easing = LinearEasing
) : Morph<T> {
    override fun at(progress: Float): T =
        interpolator.interpolate(start, end, easing.transform(progress))
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

internal object Interpolators {
    val bounds = Interpolator<Bounds> { s, e, f ->
        Bounds(
            start = lerp(s.start, e.start, f),
            top = lerp(s.top, e.top, f),
            width = lerp(s.width, e.width, f),
            height = lerp(s.height, e.height, f)
        )
    }
    val dp = Interpolator<Dp> { s, e, f -> lerp(s, e, f) }
    val textUnit = Interpolator<TextUnit> { s, e, f -> lerp(s, e, f) }
    val float = Interpolator<Float> { s, e, f -> lerp(s, e, f) }
}

internal fun morph(
    start: Bounds,
    end: Bounds,
    easing: Easing = LinearEasing,
    interpolator: Interpolator<Bounds> = Interpolators.bounds
): Morph<Bounds> = Interpolated(start, end, interpolator, easing)

internal fun morph(
    start: Dp,
    end: Dp,
    easing: Easing = LinearEasing,
    interpolator: Interpolator<Dp> = Interpolators.dp
): Morph<Dp> = Interpolated(start, end, interpolator, easing)

internal fun morph(
    start: TextUnit,
    end: TextUnit,
    easing: Easing = LinearEasing,
    interpolator: Interpolator<TextUnit> = Interpolators.textUnit
): Morph<TextUnit> = Interpolated(start, end, interpolator, easing)

internal fun morph(
    start: Float,
    end: Float,
    easing: Easing = LinearEasing,
    interpolator: Interpolator<Float> = Interpolators.float
): Morph<Float> = Interpolated(start, end, interpolator, easing)

internal fun <T> constant(value: T): Morph<T> = Constant(value)

@Composable
internal fun Morph<Float>.rememberIsVisible(
    progress: MotionProgress,
    threshold: Float = 0.01f
): State<Boolean> = remember(this, progress) {
    derivedStateOf { at(progress.value) > threshold }
}

internal fun windowedLerp(
    startValue: Dp,
    endValue: Dp,
    windowStart: Float,
    windowEnd: Float,
    fraction: Float
) = when {
    fraction <= windowStart -> startValue

    fraction >= windowEnd -> endValue

    else ->
        startValue + (endValue - startValue) * (fraction - windowStart) / (windowEnd - windowStart)
}
