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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@Composable
fun MainContentPage(
    appBarState: TopAppBarState?,
    indicatorState: ScrollIndicatorState?,
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

    val scale by transition.animateFloat(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state -> targetValueByState(state, 0.95F, 1F) },
        label = "main-content-scale"
    )

    val cornerRadius by transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state -> targetValueByState(state, 24.dp, 0.dp) },
        label = "main-content-corner-radius"
    )

    val animatedBottomPlaceholderCompensation by transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state ->
            targetValueByState(state, animationState.bottomPaddingForCompensation, 0.dp)
        },
        label = "main-content-bottom-compensation"
    )

    val elevation by transition.animateDp(
        transitionSpec = { mainContentPageAnimationSpec(targetState, isPredictiveBack) },
        targetValueByState = { state -> targetValueByState(state, 4.dp, 0.dp) },
        label = "main-content-elevation"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val compensationPx = animatedBottomPlaceholderCompensation.toPx()

                scaleX = scale
                scaleY = if (size.height > 0F) {
                    scale * (size.height - compensationPx) / size.height
                } else {
                    scale
                }
                translationY = -0.5F * compensationPx * (1 - scale)
            }
            .padding(bottom = animatedBottomPlaceholderCompensation),
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = elevation,
        content = content
    )
}
