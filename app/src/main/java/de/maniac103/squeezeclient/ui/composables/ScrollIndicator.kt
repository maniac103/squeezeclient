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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

@Composable
internal fun ScrollIndicator(text: String?, visible: Boolean) =
    AnimatedVisibility(visible = visible && text != null) {
        val backgroundColor = MaterialTheme.colorScheme.primaryContainer
        Text(
            text = text ?: "",
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .drawWithCache {
                    val notchRadius = 16.dp.toPx()
                    val w = size.width
                    val h = size.height
                    val isRtl = layoutDirection == LayoutDirection.Rtl
                    val getX = { x: Float -> if (isRtl) w - x else x }

                    val vertices = floatArrayOf(
                        getX(0f), 0f, // Top-left
                        getX(w - notchRadius), 0f, // Before notch slant
                        getX(w), h / 2f, // Tip of the notch point
                        getX(w - notchRadius), h, // After notch slant
                        getX(0f), h // Bottom-left
                    )

                    val polygon = RoundedPolygon(
                        vertices = vertices,
                        rounding = CornerRounding(radius = 8.dp.toPx())
                    )
                    val polygonPath = polygon.toPath().asComposePath()

                    onDrawBehind {
                        drawPath(polygonPath, color = backgroundColor)
                    }
                }
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        )
    }
