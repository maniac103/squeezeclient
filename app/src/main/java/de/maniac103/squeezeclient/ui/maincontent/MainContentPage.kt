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

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@Composable
private fun Transition<EnterExitState>.animateFloatForPredictiveBack(
    valueDuringAnimation: Float,
    valueDuringEnterAnimation: Float = valueDuringAnimation,
    valueDuringExitAnimation: Float = valueDuringAnimation,
    settledValue: Float,
    animation: FiniteAnimationSpec<Float>,
    label: String
): State<Float> {
    val isPredictiveBack =
        LocalMainContentAnimationState.current.animType == MainContentAnimationType.PredictiveBack

    return animateFloat(
        transitionSpec = {
            if (!isPredictiveBack) {
                tween(durationMillis = 0)
            } else {
                animation
            }
        },
        label = label
    ) { state ->
        when (state) {
            EnterExitState.PreEnter if isPredictiveBack -> valueDuringEnterAnimation
            EnterExitState.PostExit if isPredictiveBack -> valueDuringExitAnimation
            else -> settledValue
        }
    }
}

@Composable
private fun Transition<EnterExitState>.animateDpForPredictiveBack(
    valueDuringAnimation: Dp,
    valueDuringEnterAnimation: Dp = valueDuringAnimation,
    valueDuringExitAnimation: Dp = valueDuringAnimation,
    settledValue: Dp,
    animation: FiniteAnimationSpec<Dp>,
    label: String
): State<Dp> {
    val isPredictiveBack =
        LocalMainContentAnimationState.current.animType == MainContentAnimationType.PredictiveBack

    return animateDp(
        transitionSpec = {
            if (!isPredictiveBack) {
                tween(durationMillis = 0)
            } else {
                animation
            }
        },
        label = label
    ) { state ->
        when (state) {
            EnterExitState.PreEnter if isPredictiveBack -> valueDuringEnterAnimation
            EnterExitState.PostExit if isPredictiveBack -> valueDuringExitAnimation
            else -> settledValue
        }
    }
}

@Composable
fun MainContentPage(
    bottomPadding: Dp,
    content: @Composable () -> Unit
) {
    val bottomPadding = LocalMainContentAnimationState.current.bottomPagePadding
    val transition = LocalNavAnimatedContentScope.current.transition

    val scale by transition.animateFloatForPredictiveBack(
        valueDuringAnimation = 0.95F,
        settledValue = 1F,
        animation = tween(
            durationMillis = 100,
            delayMillis = 300,
            easing = DecelerateQuintEasing,
        ),
        label = "main-content-scale",
    )

    val cornerRadius by transition.animateDpForPredictiveBack(
        valueDuringAnimation = 24.dp,
        settledValue = 0.dp,
        animation = tween(
            durationMillis = 100,
            delayMillis = 300,
            easing = DecelerateQuintEasing,
        ),
        label = "main-content-corner-radius",
    )

    val animatedBottomPlaceholderCompensation by transition.animateDpForPredictiveBack(
        valueDuringAnimation = bottomPadding,
        settledValue = 0.dp,
        animation = tween(
            durationMillis = 100,
            delayMillis = 300,
            easing = DecelerateQuintEasing,
        ),
        label = "main-content-bottom-compensation",
    )

    val elevation by transition.animateDpForPredictiveBack(
        valueDuringAnimation = 4.dp,
        valueDuringExitAnimation = 0.dp,
        settledValue = 0.dp,
        animation = tween(
            durationMillis = 100,
            delayMillis = 300,
        ),
        label = "main-content-elevation",
    )

    Surface(
        modifier = Modifier
            .graphicsLayer {
                val compensationPx = animatedBottomPlaceholderCompensation.toPx()

                scaleX = scale
                scaleY = if (size.height > 0f) {
                    scale * (size.height - compensationPx) / size.height
                } else {
                    scale
                }
                translationY = -0.5f * compensationPx * scale
            }
            .padding(bottom = bottomPadding),
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = elevation,
    ) {
        content()
    }
}