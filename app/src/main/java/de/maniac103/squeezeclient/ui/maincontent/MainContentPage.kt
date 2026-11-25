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
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@Composable
fun MainContentPage(
    appBarState: TopAppBarState?,
    indicatorState: ScrollIndicatorState?,
    contentPaddingOfPageContent: PaddingValues,
    content: @Composable () -> Unit
) {
    val animationState = LocalMainContentAnimationState.current
    val transition = LocalNavAnimatedContentScope.current.transition
    val isPredictiveBack = animationState.animType == MainContentAnimationType.PredictiveBack

    if (appBarState != null) {
        val pageIsActive = transition.targetState == EnterExitState.Visible && !isPredictiveBack

        LaunchedEffect(pageIsActive, appBarState) {
            if (!pageIsActive) return@LaunchedEffect
            snapshotFlow { indicatorState?.scrollOffset ?: 0 }
                .collect { px -> appBarState.contentOffset = -px.toFloat() }
        }
    }

    val animationRunning = transition.currentState != transition.targetState
    val animationStateReporter by rememberUpdatedState(animationState.onTransitionRunningChanged)

    DisposableEffect(animationRunning, animationStateReporter) {
        animationStateReporter(animationRunning)
        onDispose {
            if (animationRunning) {
                animationStateReporter(false)
            }
        }
    }

    fun <T> targetValueByState(state: EnterExitState, animatedValue: T, settledValue: T) =
        when (state) {
            EnterExitState.PreEnter if isPredictiveBack -> animatedValue
            EnterExitState.PostExit if isPredictiveBack -> animatedValue
            else -> settledValue
        }

    val scale = transition.animateFloat(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state -> targetValueByState(state, 0.97F, 1F) },
        label = "main-content-scale"
    )

    val cornerRadius = transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state -> targetValueByState(state, 24.dp, 0.dp) },
        label = "main-content-corner-radius"
    )

    val layoutDirection = LocalLayoutDirection.current
    val startInset = transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state ->
            val animatedValue = contentPaddingOfPageContent.calculateStartPadding(layoutDirection)
            targetValueByState(state, animatedValue, 0.dp)
        },
        label = "main-content-start-inset"
    )
    val endInset = transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state ->
            val animatedValue = contentPaddingOfPageContent.calculateEndPadding(layoutDirection)
            targetValueByState(state, animatedValue, 0.dp)
        },
        label = "main-content-end-inset"
    )

    val bottomPlaceholderCompensation = transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state ->
            targetValueByState(state, animationState.bottomPaddingForCompensation, 0.dp)
        },
        label = "main-content-bottom-compensation"
    )

    val elevation = transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state -> targetValueByState(state, 4.dp, 0.dp) },
        label = "main-content-elevation"
    )

    val runAnimationState = animationState.runAnimation
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val (compensationPx, scale) = when (runAnimationState.value) {
                    true -> bottomPlaceholderCompensation.value.toPx() to scale.value
                    else -> 0F to 1F
                }

                scaleX = scale
                scaleY = if (size.height > 0F) {
                    scale * (size.height - compensationPx) / size.height
                } else {
                    scale
                }
                translationY = -0.5F * compensationPx * (1 - scale)
            }
            .layout { measurable, constraints ->
                val bottom = when (runAnimationState.value) {
                    true -> bottomPlaceholderCompensation.value.roundToPx()
                    else -> 0
                }
                val placeable = measurable.measure(constraints.offset(vertical = -bottom))
                layout(
                    constraints.constrainWidth(placeable.width),
                    constraints.constrainHeight(placeable.height + bottom)
                ) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                if (runAnimationState.value) {
                    shape = HorizontalInsetRoundedShape(
                        startInset = startInset.value,
                        endInset = endInset.value,
                        cornerRadius = cornerRadius.value
                    )
                    clip = true
                    shadowElevation = elevation.value.toPx()
                }
            }
    )
}

private data class HorizontalInsetRoundedShape(
    val startInset: Dp,
    val endInset: Dp,
    val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val leftInset = (if (isRtl) endInset else startInset).toPx()
        val rightInset = (if (isRtl) startInset else endInset).toPx()
        Outline.Rounded(
            RoundRect(
                left = leftInset,
                top = 0f,
                right = size.width - rightInset,
                bottom = size.height,
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        )
    }
}
