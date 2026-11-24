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

package de.maniac103.squeezeclient.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

@Composable
fun DelayedAnimatedVisibility(
    show: Boolean,
    modifier: Modifier = Modifier,
    showDelay: Duration = 100.milliseconds,
    minVisible: Duration = 300.milliseconds,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    contentWhenHidden: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var shownAt by remember { mutableStateOf<TimeMark?>(null) }
    val visible by remember { computedStateOf { shownAt != null } }
    val useDelays = LocalInspectionMode.current.not()

    LaunchedEffect(show) {
        if (show && !visible) {
            if (useDelays) delay(showDelay)
            shownAt = TimeSource.Monotonic.markNow()
        } else if (!show && visible) {
            val remaining = minVisible - (shownAt?.elapsedNow() ?: Duration.ZERO)
            if (useDelays && remaining.isPositive()) delay(remaining)
            shownAt = null
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit
    ) {
        content()
    }
    if (contentWhenHidden != null) {
        AnimatedVisibility(
            visible = !visible,
            modifier = modifier,
            enter = enter,
            exit = exit
        ) {
            contentWhenHidden()
        }
    }
}
