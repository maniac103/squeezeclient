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

package de.maniac103.squeezeclient.ui.maincontent

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

internal enum class MainContentAnimationType {
    Normal,
    PredictiveBack
}

internal data class MainContentAnimationState(
    val animType: MainContentAnimationType,
    val bottomPagePadding: Dp
)

internal val LocalMainContentAnimationState = staticCompositionLocalOf {
    MainContentAnimationState(MainContentAnimationType.Normal, 0.dp)
}

internal val AccelerateDecelerateEasing =
    Easing { fraction ->
        ((cos((fraction + 1f) * PI) / 2.0) + 0.5).toFloat()
    }

internal val DecelerateCubicEasing =
    Easing { fraction ->
        1f - (1f - fraction).let { it * it * it }
    }

internal val DecelerateQuintEasing =
    Easing { fraction ->
        val x = 1f - fraction
        1f - x * x * x * x * x
    }

private const val ForwardDuration = 300
private const val PopEnterDuration = 300

internal fun mainContentEnter(): EnterTransition {
    return (
        slideInHorizontally(
            initialOffsetX = { width -> width },
            animationSpec = tween(
                durationMillis = ForwardDuration,
                easing = AccelerateDecelerateEasing,
            ),
        ) +
            fadeIn(
                initialAlpha = 0.5f,
                animationSpec = tween(
                    durationMillis = ForwardDuration,
                    easing = DecelerateCubicEasing,
                ),
            )
        )
}

internal fun mainContentExit(): ExitTransition {
    return (
        slideOutHorizontally(
            targetOffsetX = { width -> -width },
            animationSpec = tween(
                durationMillis = ForwardDuration,
                easing = AccelerateDecelerateEasing,
            ),
        ) +
            fadeOut(
                targetAlpha = 0.5f,
                animationSpec = tween(
                    durationMillis = ForwardDuration,
                    easing = DecelerateCubicEasing,
                ),
            )
        )
}

internal fun mainContentPopEnter(): EnterTransition {
    return (
        slideInHorizontally(
            initialOffsetX = { width -> -width },
            animationSpec = tween(
                durationMillis = PopEnterDuration,
                easing = AccelerateDecelerateEasing,
            ),
        ) +
            fadeIn(
                initialAlpha = 0.5f,
                animationSpec = tween(
                    durationMillis = PopEnterDuration,
                    easing = DecelerateCubicEasing,
                ),
            )
        )
}

internal fun mainContentPopExit(): ExitTransition {
    return (
        slideOutHorizontally(
            targetOffsetX = { width -> width },
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = 101,
                easing = AccelerateDecelerateEasing,
            )
        ) +
            fadeOut(
                targetAlpha = 0.5f,
                animationSpec = tween(
                    durationMillis = 200,
                    delayMillis = 201,
                    easing = DecelerateCubicEasing,
                ),
            )
        )
}