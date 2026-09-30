/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
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

package de.maniac103.squeezeclient.ui.slideshow

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.addServerCredentialsIfNeeded
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.SlideshowImage
import kotlin.io.path.createTempFile

class ImageViewActivity : AppCompatActivity() {
    private val item get() =
        IntentCompat.getParcelableExtra(intent, EXTRA_ITEM, SlideshowImage::class.java)!!

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val imageUrl = item.imageUrl.let { url ->
                prefs.serverConfig?.url?.resolve(url)?.toString() ?: url
            }

            val imageRequest = ImageRequest.Builder(this)
                .data(imageUrl)
                .addServerCredentialsIfNeeded(this)
                .build()

            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    var imageIsLoading by remember { mutableStateOf(true) }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(text = item.caption) },
                                colors = TopAppBarDefaults.topAppBarColors().copy(
                                    containerColor = Color.Transparent
                                ),
                                navigationIcon = {
                                    IconButton(
                                        onClick = { finishAfterTransition() }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_left_24dp),
                                            contentDescription = null // FIXME
                                        )
                                    }
                                },
                                actions = {
                                    if (!imageIsLoading) {
                                        IconButton(
                                            onClick = { share(imageUrl) }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_share_24dp),
                                                contentDescription = null // FIXME
                                            )
                                        }

                                    }
                                },
                                modifier = Modifier.background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.75F),
                                            Color.Transparent
                                        )
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { _ ->
                        if (imageIsLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 6.dp,
                                modifier = Modifier.size(128.dp)
                            )
                        }
                    }
                    ZoomableAsyncImage(
                        imageRequest = imageRequest,
                        contentDescription = item.caption,
                        loadingStateListener = { state ->
                            imageIsLoading = state is AsyncImagePainter.State.Loading
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun share(imageUrl: String) {
        val extension = MimeTypeMap.getFileExtensionFromUrl(imageUrl)
        val uri = imageLoader.diskCache?.openSnapshot(imageUrl)?.use { snapshot ->
            val tempFile = createTempFile(
                snapshot.data.parent?.toNioPath(),
                "share_image",
                ".$extension"
            ).toFile()
            snapshot.data.toFile().copyTo(tempFile, true)
            FileProvider.getUriForFile(this, "$packageName.cachefiles", tempFile)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(null, uri)
        }
        val chooserIntent = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(chooserIntent)
    }

    companion object {
        private const val EXTRA_ITEM = "item"

        fun createIntent(context: Context, item: SlideshowImage) =
            Intent(context, ImageViewActivity::class.java)
                .putExtra(EXTRA_ITEM, item)
    }
}

@Composable
fun ZoomableAsyncImage(
    imageRequest: ImageRequest,
    contentDescription: String,
    loadingStateListener: (AsyncImagePainter.State) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1F) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        onState = { loadingStateListener(it) },
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Update the scale based on zoom gestures.
                    scale *= zoom

                    // Limit the zoom levels within a certain range (optional).
                    scale = scale.coerceIn(0.5f, 3f)

                    // Update the offset to implement panning when zoomed.
                    offset = if (scale == 1f) Offset(0f, 0f) else offset + pan
                }
            }
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offset.x, translationY = offset.y
            )
    )
}