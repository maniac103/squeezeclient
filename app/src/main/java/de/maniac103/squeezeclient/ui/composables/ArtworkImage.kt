package de.maniac103.squeezeclient.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.placeholder
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.loadMaybeRelativeUrl
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration

@Composable
fun ArtworkImage(
    artwork: ArtworkItem,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier,
    usePlaceholder: Boolean = true
) {
    var currentAspectRatio by remember { mutableStateOf<Float?>(null) }

    val imageRequest = ImageRequest.Builder(LocalContext.current)
        .loadMaybeRelativeUrl(artwork.extractIconUrl(), serverConfig)
        .apply {
            if (usePlaceholder) {
                placeholder(R.drawable.ic_disc_24dp)
            }
        }
        .build()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { state ->
                val painter = when (state) {
                    is AsyncImagePainter.State.Loading -> state.painter
                    is AsyncImagePainter.State.Success -> state.painter
                    is AsyncImagePainter.State.Error -> state.painter
                    is AsyncImagePainter.State.Empty -> null
                }

                val size = painter?.intrinsicSize
                if (size != null && size.width > 0 && size.height > 0) {
                    currentAspectRatio = size.width / size.height
                }
            },
            modifier = Modifier
                .then(
                    currentAspectRatio?.let { Modifier.aspectRatio(it) }
                        ?: Modifier
                )
                .clip(RoundedCornerShape(20 /* percent */))
        )
    }
}