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

package de.maniac103.squeezeclient.extfuncs

import android.content.DialogInterface
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil.load
import coil.request.ImageRequest
import com.google.android.material.appbar.AppBarLayout
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.ui.widget.AbstractMotionLayoutTransitionListener
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine

fun View.animateScale(scale: Float, duration: Duration) =
    animate().scaleX(scale).scaleY(scale).setDuration(duration.inWholeMilliseconds)

fun ImageView.loadArtwork(item: ArtworkItem?, builder: ImageRequest.Builder.() -> Unit = {}) =
    load(item?.extractIconUrl(context)) {
        addServerCredentialsIfNeeded(context)
        target(RoundedCornerImageViewTarget(this@loadArtwork))
        builder()
    }

fun ImageView.loadArtworkOrPlaceholder(
    item: ArtworkItem?,
    builder: ImageRequest.Builder.() -> Unit = {}
) = loadArtwork(item) {
    placeholder(ContextCompat.getDrawable(context, R.drawable.ic_disc_24dp))
    builder()
}

fun ImageView.loadSlideshowImage(
    item: SlideshowImage,
    roundCorners: Boolean,
    builder: ImageRequest.Builder.() -> Unit = {}
) = item.imageUrl.let { url ->
    val baseUrl = context.prefs.serverConfig?.url
    val absoluteUrl = baseUrl?.resolve(url)?.toString() ?: url
    load(absoluteUrl) {
        addServerCredentialsIfNeeded(context)
        if (roundCorners) {
            target(RoundedCornerImageViewTarget(this@loadSlideshowImage))
        }
        builder()
    }
}

fun View.addSystemBarAndCutoutInsetsListener(applyTop: Boolean, applyBottom: Boolean) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
        val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val startIsLeft = layoutDirection == View.LAYOUT_DIRECTION_LTR
        val leftBarInset = if (startIsLeft) barInsets.left else 0
        val rightBarInset = if (startIsLeft) 0 else barInsets.right
        v.updatePadding(
            left = cutoutInsets.left + leftBarInset,
            right = cutoutInsets.right + rightBarInset
        )
        if (applyTop) {
            v.updatePadding(top = barInsets.top)
        }
        if (applyBottom) {
            v.updatePadding(bottom = barInsets.bottom)
        }
        windowInsets
    }
}

fun AppBarLayout.addSystemBarAndCutoutInsetsListener() {
    addSystemBarAndCutoutInsetsListener(applyTop = true, applyBottom = false)
}

fun View.addContentSystemBarAndCutoutInsetsListener() {
    addSystemBarAndCutoutInsetsListener(applyTop = false, applyBottom = true)
}

inline fun MotionLayout.doOnTransitionCompleted(crossinline action: (id: Int) -> Unit) {
    addTransitionListener(object : AbstractMotionLayoutTransitionListener() {
        override fun onTransitionCompleted(layout: MotionLayout?, currentId: Int) {
            action.invoke(currentId)
        }
    })
}

suspend fun AlertDialog.await(positiveText: String, negativeText: String) =
    suspendCancellableCoroutine { cont ->
        val listener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                AlertDialog.BUTTON_POSITIVE -> cont.resume(true)
                AlertDialog.BUTTON_NEGATIVE -> cont.resume(false)
            }
        }

        setButton(AlertDialog.BUTTON_POSITIVE, positiveText, listener)
        setButton(AlertDialog.BUTTON_NEGATIVE, negativeText, listener)

        setOnCancelListener { cont.resume(false) }
        cont.invokeOnCancellation { dismiss() }
        show()
    }
