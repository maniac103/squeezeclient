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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.size.Precision
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.ActivityImageBinding
import de.maniac103.squeezeclient.extfuncs.loadSlideshowImage
import de.maniac103.squeezeclient.model.SlideshowImage
import kotlin.io.path.createTempFile

class ImageViewActivity : AppCompatActivity() {
    private val item get() =
        IntentCompat.getParcelableExtra(intent, EXTRA_ITEM, SlideshowImage::class.java)!!

    private lateinit var binding: ActivityImageBinding
    private lateinit var imageUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xA0000000.toInt())
        )
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.image.loadSlideshowImage(item) {
            lifecycle(lifecycle)
            precision(Precision.INEXACT)
            crossfade(true)
            listener { request, _ ->
                imageUrl = request.data.toString()
                binding.progress.isVisible = false
                binding.toolbar.inflateMenu(R.menu.image_view_toolbar)
            }
        }

        binding.toolbar.apply {
            title = item.caption
            setNavigationOnClickListener {
                finishAfterTransition()
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.share -> {
                        share()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun share() {
        val extension = MimeTypeMap.getFileExtensionFromUrl(item.imageUrl)
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

        fun createIntent(context: Context, item: SlideshowImage): Intent {
            return Intent(context, ImageViewActivity::class.java).apply {
                putExtra(EXTRA_ITEM, item)
            }
        }
    }
}
