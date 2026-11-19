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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

@Composable
fun DelayedAnimatedVisibility(
    show: Boolean,
    showDelay: Duration = 100.milliseconds,
    minVisible: Duration = 300.milliseconds,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf<TimeMark?>(null) }

    LaunchedEffect(show) {
        if (show) {
            if (!visible) {
                delay(showDelay)
                shownAt = TimeSource.Monotonic.markNow()
                visible = true
            }
        } else if (visible) {
            val remaining = minVisible - (shownAt?.elapsedNow() ?: Duration.ZERO)
            if (remaining.isPositive()) delay(remaining)
            visible = false
        }
    }

    AnimatedVisibility(visible = visible) {
        content()
    }
}
