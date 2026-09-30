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

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import de.maniac103.squeezeclient.extfuncs.getParcelableList
import de.maniac103.squeezeclient.extfuncs.loadMaybeRelativeUrl
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.ui.MainContentChild
import de.maniac103.squeezeclient.ui.common.ComposeFragment
import kotlinx.coroutines.flow.flowOf

class GalleryFragment : ComposeFragment(), MainContentChild {
    private val items get() = requireArguments().getParcelableList("items", SlideshowImage::class)
    override val titleFlow get() = flowOf(requireArguments().getStringArrayList("title")!!)
    override val iconFlow get() = flowOf(null)
    override val scrollingTargetView get() = null // FIXME

    @Composable
    override fun createContent() =
        GalleryGrid(items, prefs.serverConfig) { item ->
            val intent = ImageViewActivity.createIntent(requireContext(), item)
            startActivity(intent)
        }

    companion object {
        fun create(items: List<SlideshowImage>, title: String, parentTitle: String?) =
            GalleryFragment().apply {
                val titleList = listOfNotNull(parentTitle, title)
                arguments = Bundle().apply {
                    putParcelableArrayList("items", ArrayList(items))
                    putStringArrayList("title", ArrayList(titleList))
                }
            }
    }
}

@Composable
fun GalleryGrid(
    items: List<SlideshowImage>,
    serverConfig: ServerConfiguration?,
    itemSelectionListener: (SlideshowImage) -> Unit = {}
) = LazyVerticalGrid(
    columns = GridCells.Fixed(2)
) {
    items(items) { item ->
        val imageRequest = ImageRequest.Builder(LocalContext.current)
            .loadMaybeRelativeUrl(item.imageUrl, serverConfig)
            .build()

        GalleryGridItem(
            imageRequest = imageRequest,
            caption = item.caption,
            modifier = Modifier
                .clickable(onClick = { itemSelectionListener(item) })
        )
    }
}

@Composable
fun GalleryGridItem(
    imageRequest: ImageRequest,
    caption: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp)
    ) {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = caption,
            loading = {
                CircularProgressIndicator(
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(64.dp)
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10 /* percent */))
                .align(Alignment.CenterHorizontally)
        )
        Text(
            text = caption,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}