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

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource

@Composable
fun ResourceIcon(
    drawableResId: Int,
    contentDescriptionResId: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) = Icon(
    imageVector = ImageVector.vectorResource(drawableResId),
    contentDescription = stringResource(contentDescriptionResId),
    tint = tint,
    modifier = modifier
)

@Composable
fun ResourceIconButton(
    drawableResId: Int,
    contentDescriptionResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    iconTint: Color = LocalContentColor.current,
    enabled: Boolean = true
) = IconButton(
    onClick = onClick,
    colors = colors,
    enabled = enabled,
    modifier = modifier
) {
    ResourceIcon(
        drawableResId = drawableResId,
        contentDescriptionResId = contentDescriptionResId,
        tint = iconTint
    )
}
