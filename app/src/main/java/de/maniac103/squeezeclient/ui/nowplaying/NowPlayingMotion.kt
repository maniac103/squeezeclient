/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package de.maniac103.squeezeclient.ui.nowplaying

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp
import kotlin.coroutines.cancellation.CancellationException

internal enum class MotionAnchor {
    Expanded,
    Collapsed
}

internal class MotionProgress(initialState: MotionAnchor) {
    private val state = AnchoredDraggableState(initialState)

    private val progressState = derivedStateOf {
        if (state.offset.isNaN()) {
            when (state.currentValue) {
                MotionAnchor.Expanded -> 1f
                MotionAnchor.Collapsed -> 0f
            }
        } else {
            state.progress(
                from = MotionAnchor.Collapsed,
                to = MotionAnchor.Expanded
            )
        }
    }

    val value get() = progressState.value
    val isExpanded get() = state.settledValue == MotionAnchor.Expanded

    fun updateDragDistance(dragDistance: Float) {
        val distance = dragDistance.coerceAtLeast(1F)
        state.updateAnchors(
            DraggableAnchors {
                MotionAnchor.Expanded at 0f
                MotionAnchor.Collapsed at distance
            }
        )
    }

    suspend fun collapse() {
        state.animateTo(MotionAnchor.Collapsed)
    }

    suspend fun expand() {
        state.animateTo(MotionAnchor.Expanded)
    }

    suspend fun moveTo(progress: Float) {
        state.anchoredDrag { anchors ->
            val collapsedOffset = anchors.positionOf(MotionAnchor.Collapsed)
            val expandedOffset = anchors.positionOf(MotionAnchor.Expanded)
            val offset = lerp(collapsedOffset, expandedOffset, progress)
            dragTo(offset)
        }
    }

    fun anchoredState() = state
}

@Composable
internal fun MotionProgressBackHandler(
    progress: MotionProgress,
    enabled: Boolean = true,
    progressScaler: (Float) -> Float = { it }
) = PredictiveBackHandler(enabled && progress.value > 0F) { progressFlow ->
    try {
        progressFlow.collect { backEvent ->
            progress.moveTo(1F - progressScaler(backEvent.progress))
        }
        progress.collapse()
    } catch (_: CancellationException) {
        progress.expand()
    }
}

internal class NowPlayingMotionState(
    initialNowPlayingState: MotionAnchor,
    initialPlaylistState: MotionAnchor
) {
    val nowPlaying = MotionProgress(initialNowPlayingState)
    val playlist = MotionProgress(initialPlaylistState)
}

@Composable
internal fun rememberNowPlayingMotionState(
    initiallyExpanded: Boolean = true,
) = remember {
    NowPlayingMotionState(
        initialNowPlayingState =
            if (initiallyExpanded) MotionAnchor.Expanded else MotionAnchor.Collapsed,
        initialPlaylistState = MotionAnchor.Collapsed
    )
}
