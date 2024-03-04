/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

internal val colorPalette = Colors(
    primary = Color(0xFF93D5AA),
    primaryVariant = Color(0xFF77D597),
    secondary = Color(0xFFB5CCBA),
    secondaryVariant = Color(0xFFA5CCAD),
    surface = Color(0xFF0F1511),
    error = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF00391F),
    onSecondary = Color(0xFF213528),
    onBackground = Color(0xFFDFE4DD),
    onSurface = Color(0xFFDFE4DD),
    onSurfaceVariant = Color(0xFFC0C9C0),
    onError = Color(0xFF690005)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = colorPalette,
        content = content
    )
}