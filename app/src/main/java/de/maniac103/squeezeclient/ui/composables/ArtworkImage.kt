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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.loadMaybeRelativeUrl
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration

@Composable
fun ArtworkImage(
    artwork: ArtworkItem?,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier,
    placeholderDrawableResId: Int? = R.drawable.ic_disc_24dp,
    fallbackDrawableResId: Int? = null,
    enforceLoadingOriginalImageSize: Boolean = false,
    cornerPercent: () -> Int = { 20 }
) {
    var currentAspectRatio by remember { mutableStateOf<Float?>(null) }
    val context = LocalContext.current
    val iconUrl = artwork?.extractIconUrl()

    val imageRequest = remember(context, iconUrl, serverConfig, enforceLoadingOriginalImageSize) {
        ImageRequest.Builder(context)
            .loadMaybeRelativeUrl(artwork?.extractIconUrl(), serverConfig)
            .apply {
                if (enforceLoadingOriginalImageSize) {
                    size(Size.ORIGINAL)
                }
            }
            .build()
    }

    val placeholder = rememberTintingPainterWrapperOrNull(placeholderDrawableResId)
    val fallback = rememberTintingPainterWrapperOrNull(
        fallbackDrawableResId ?: placeholderDrawableResId
    )

    val updateAspectRatio: (AsyncImagePainter.State) -> Unit = { state ->
        val size = state.painter?.intrinsicSize
        if (size != null && size.width > 0 && size.height > 0) {
            currentAspectRatio = size.width / size.height
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            placeholder = placeholder,
            fallback = fallback,
            onLoading = updateAspectRatio,
            onSuccess = updateAspectRatio,
            onError = updateAspectRatio,
            modifier = Modifier
                .then(
                    currentAspectRatio?.let { Modifier.aspectRatio(it) }
                        ?: Modifier
                )
                .graphicsLayer {
                    shape = RoundedCornerShape(percent = cornerPercent())
                    clip = true
                }
        )
    }
}

// See https://coil-kt.github.io/coil/recipes/#transforming-painters
private class TintingPainterWrapper(private val painter: Painter, tint: Color) : Painter() {
    private val colorFilter = ColorFilter.tint(tint)

    override fun DrawScope.onDraw() {
        with(painter) {
            draw(size, colorFilter = colorFilter)
        }
    }

    override val intrinsicSize get() = painter.intrinsicSize
}

@Composable
private fun rememberTintingPainterWrapperOrNull(drawableResId: Int?) = drawableResId?.let {
    val painter = painterResource(it)
    val tintColor = MaterialTheme.colorScheme.primary
    remember(painter, tintColor) {
        TintingPainterWrapper(painter, tintColor)
    }
}
