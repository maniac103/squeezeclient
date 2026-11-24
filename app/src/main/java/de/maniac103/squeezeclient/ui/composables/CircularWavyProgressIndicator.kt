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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TinyCircularWavyProgressIndicator(modifier: Modifier = Modifier) {
    CircularWavyProgressIndicator(
        stroke = createStroke(2.dp),
        trackStroke = createStroke(2.dp),
        wavelength = 6.dp,
        gapSize = 3.dp,
        modifier = modifier
            .size(16.dp)
    )
}

@Composable
fun SmallCircularWavyProgressIndicator(modifier: Modifier = Modifier) {
    CircularWavyProgressIndicator(
        stroke = createStroke(3.dp),
        trackStroke = createStroke(3.dp),
        wavelength = 8.dp,
        gapSize = 4.dp,
        modifier = modifier
            .size(24.dp)
    )
}

@Composable
fun HugeCircularWavyProgressIndicator(modifier: Modifier = Modifier) {
    CircularWavyProgressIndicator(
        wavelength = 32.dp,
        amplitude = 0.6F,
        gapSize = 16.dp,
        modifier = modifier
            .size(192.dp)
    )
}

@Composable
private fun createStroke(strokeWidth: Dp) = Stroke(
    width = with(LocalDensity.current) { strokeWidth.toPx() },
    cap = StrokeCap.Round
)
