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

package de.maniac103.squeezeclient.ui.nowplaying

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseInQuint
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import de.maniac103.squeezeclient.ui.maincontent.MainLayout

@Immutable
internal sealed interface PlayerGeometryInput {
    val containerSize: DpSize
    val cornerSize: Dp
    val playlistMinSidePadding: Dp

    data class Portrait(
        override val containerSize: DpSize,
        override val cornerSize: Dp,
        override val playlistMinSidePadding: Dp,
        val collapsedHeight: Dp
    ) : PlayerGeometryInput

    data class Landscape(
        override val containerSize: DpSize,
        override val cornerSize: Dp,
        override val playlistMinSidePadding: Dp,
        val collapsedWidth: Dp
    ) : PlayerGeometryInput

    data class Foldable(
        override val containerSize: DpSize,
        override val cornerSize: Dp,
        override val playlistMinSidePadding: Dp,
        val paneWidth: Dp
    ) : PlayerGeometryInput
}

@Immutable
internal data class PlaylistLayoutSpec(
    // driven by now playing progress
    val bounds: Morph<Bounds>,
    val alpha: Morph<Float>,
    val cornerSizes: CornerSizes,
    val topSpacing: Morph<Dp>,
    // driven by playlist progress
    val contentAlpha: Morph<Float>,
    // static
    val contentPadding: PaddingValues,
    val handleHeight: Dp = Dp.Unspecified,
    val handleDragDistance: Float = 0F
) {
    val needsHandle get() = handleHeight.isSpecified
}

@Immutable
internal data class ControlLayoutSpec(val bounds: Morph<Bounds>, val alpha: Morph<Float>)

@Immutable
internal data class TextLayoutSpec(
    val bounds: Morph<Bounds>,
    val fontSize: Morph<TextUnit>,
    val alpha: Morph<Float> = constant(1F)
)

@Immutable
internal data class ProgressLayoutSpec(val mini: ControlLayoutSpec, val full: ControlLayoutSpec)

@Immutable
internal data class CornerSizes(
    val topStart: Morph<Dp>,
    val topEnd: Morph<Dp>,
    val bottomStart: Morph<Dp>,
    val bottomEnd: Morph<Dp>
)

@Immutable
internal data class ContentLayoutSpec(
    val bounds: Morph<Bounds>,
    val corners: CornerSizes,
    val dragDirection: Orientation?,
    val dragDistance: Float
)

@Immutable
internal data class ControlsLayoutSpec(
    val repeat: ControlLayoutSpec,
    val prevTrack: ControlLayoutSpec,
    val playPause: ControlLayoutSpec,
    val nextTrack: ControlLayoutSpec,
    val shuffle: ControlLayoutSpec
) {
    internal fun withReversedOrder() = copy(
        repeat = repeat.copy(bounds = shuffle.bounds),
        shuffle = shuffle.copy(bounds = repeat.bounds),
        prevTrack = prevTrack.copy(bounds = nextTrack.bounds),
        nextTrack = nextTrack.copy(bounds = prevTrack.bounds)
    )
}

@Immutable
internal data class ArtworkLayoutSpec(val bounds: Morph<Bounds>, val cornerPercentage: Morph<Float>)

@Immutable
internal data class PlayerGeometry(
    val content: ContentLayoutSpec,
    val playlist: PlaylistLayoutSpec,
    val progress: ProgressLayoutSpec,
    val toolbar: ControlLayoutSpec,

    val artwork: ArtworkLayoutSpec,
    val controls: ControlsLayoutSpec,

    val title: TextLayoutSpec,
    val artist: TextLayoutSpec,
    val album: TextLayoutSpec,
    val textAlignmentProgress: Morph<Float>
)

private fun calculatePlayerGeometry(
    input: PlayerGeometryInput,
    density: Density,
    layoutDirection: LayoutDirection,
    insets: PaddingValues,
    hasArtist: Boolean,
    hasAlbum: Boolean
): PlayerGeometry = when (input) {
    is PlayerGeometryInput.Portrait ->
        calculatePortraitGeometry(
            density = density,
            layoutDirection = layoutDirection,
            input = input,
            insets = insets,
            hasAlbum = hasAlbum,
            hasArtist = hasArtist,
            playlistPeekHeight = 48.dp
        )

    is PlayerGeometryInput.Landscape -> {
        val isLarge = input.containerSize.height >= 480.dp
        calculateLandscapeGeometry(
            density = density,
            layoutDirection = layoutDirection,
            input = input,
            insets = insets,
            hasAlbum = hasAlbum,
            hasArtist = hasArtist,
            collapsedTitleSize = 18.sp,
            expandedTitleSize = if (isLarge) 24.sp else 20.sp,
            collapsedSecondaryTextSize = if (isLarge) 18.sp else 16.sp,
            expandedSecondaryTextSize = if (isLarge) 20.sp else 16.sp,
            expandedArtworkHeightFraction = if (isLarge) 0.5F else 0.35F,
            collapsedPlaylistHeightFraction = if (isLarge) 0.5F else 0.0F,
            expandedPlaylistWidth = max(input.collapsedWidth, input.containerSize.width * 0.3F)
        )
    }

    is PlayerGeometryInput.Foldable -> {
        calculateFoldablePaneGeometry(
            density = density,
            layoutDirection = layoutDirection,
            input = input,
            insets = insets,
            hasAlbum = hasAlbum,
            hasArtist = hasArtist
        )
    }
}

private fun calculatePortraitGeometry(
    density: Density,
    layoutDirection: LayoutDirection,
    input: PlayerGeometryInput.Portrait,
    insets: PaddingValues,
    hasAlbum: Boolean,
    hasArtist: Boolean,
    playlistPeekHeight: Dp
): PlayerGeometry {
    val containerSize = input.containerSize
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val startInset = insets.calculateStartPadding(layoutDirection)
    val endInset = insets.calculateEndPadding(layoutDirection)

    // Constants
    val toolbarHeight = 64.dp
    val miniProgressHeight = 4.dp
    val collapsedArtworkSize = 48.dp
    val collapsedPlayPauseSize = 48.dp
    val collapsedOtherControlsSize = 40.dp
    val collapsedControlSpacing = 4.dp
    val collapsedHorizontalSpacing = 8.dp
    val collapsedTitleSize = 16.sp
    val collapsedSecondarySize = 14.sp
    val expandedProgressSize = 64.dp
    val expandedPlayPauseSize = 72.dp
    val expandedOtherControlsSize = 48.dp
    val expandedTextHorizontalPadding = 8.dp
    val expandedControlsHorizontalPadding = 16.dp
    val expandedProgressHorizontalPadding = 16.dp
    val expandedTitleSize = 24.sp
    val expandedSecondarySize = 18.sp

    // Collapsed state

    val collapsedBackground = Bounds(
        start = 0.dp,
        top = containerSize.height - input.collapsedHeight - bottomInset,
        width = containerSize.width,
        height = input.collapsedHeight + bottomInset
    )

    val collapsedContent = Bounds(
        start = startInset,
        top = 0.dp,
        width = containerSize.width - startInset - endInset,
        height = input.collapsedHeight
    )

    val collapsedPlaylist = Bounds(
        start = 0.dp,
        top = collapsedContent.bottom,
        width = containerSize.width,
        height = containerSize.height
    )

    val collapsedMiniProgress = Bounds(
        start = 0.dp,
        top = collapsedContent.height - miniProgressHeight,
        width = collapsedContent.width,
        height = miniProgressHeight
    )

    val collapsedArtwork = Bounds(
        start = collapsedHorizontalSpacing,
        top = (collapsedMiniProgress.top - collapsedArtworkSize) / 2,
        width = collapsedArtworkSize,
        height = collapsedArtworkSize
    )

    val collapsedControlsWidth =
        collapsedPlayPauseSize + collapsedOtherControlsSize * 2 + collapsedControlSpacing * 2
    val collapsedControlsStart =
        collapsedContent.width - collapsedHorizontalSpacing - collapsedControlsWidth
    val collapsedPlayPauseTop = (collapsedMiniProgress.top - collapsedPlayPauseSize) / 2
    val collapsedOtherControlsTop =
        collapsedPlayPauseTop + (collapsedPlayPauseSize - collapsedOtherControlsSize) / 2

    val collapsedRepeat = Bounds(
        start = collapsedControlsStart,
        top = collapsedOtherControlsTop,
        width = 0.dp,
        height = collapsedOtherControlsSize
    )
    val collapsedPrevTrack = Bounds(
        start = collapsedControlsStart,
        top = collapsedOtherControlsTop,
        width = collapsedOtherControlsSize,
        height = collapsedOtherControlsSize
    )
    val collapsedPlayPause = Bounds(
        start = collapsedPrevTrack.end + collapsedControlSpacing,
        top = collapsedPlayPauseTop,
        width = collapsedPlayPauseSize,
        height = collapsedPlayPauseSize
    )
    val collapsedNextTrack = Bounds(
        start = collapsedPlayPause.end + collapsedControlSpacing,
        top = collapsedOtherControlsTop,
        width = collapsedOtherControlsSize,
        height = collapsedOtherControlsSize
    )
    val collapsedShuffle = Bounds(
        start = collapsedNextTrack.end,
        top = collapsedOtherControlsTop,
        width = 0.dp,
        height = collapsedOtherControlsSize
    )

    val collapsedTextStart = collapsedArtwork.end + collapsedHorizontalSpacing
    val collapsedTextWidth =
        collapsedControlsStart - collapsedTextStart - collapsedHorizontalSpacing
    val collapsedTitleHeight = collapsedTitleSize.estimateLayoutHeight(density)
    val collapsedSecondaryHeight = collapsedSecondarySize.estimateLayoutHeight(density)
    val collapsedArtistHeight = if (hasArtist) collapsedSecondaryHeight else 0.dp
    val collapsedTextSpacing =
        (collapsedArtwork.height - collapsedTitleHeight - collapsedArtistHeight) / 2
    val collapsedTitle = Bounds(
        start = collapsedTextStart,
        top = collapsedArtwork.top + collapsedTextSpacing,
        width = collapsedTextWidth,
        height = collapsedTitleHeight
    )
    val collapsedArtist = Bounds(
        start = collapsedTextStart,
        top = collapsedTitle.bottom,
        width = collapsedTextWidth,
        height = collapsedArtistHeight
    )
    val collapsedAlbum = Bounds(
        start = collapsedTextStart,
        top = collapsedArtist.bottom,
        width = collapsedTextWidth,
        height = 0.dp
    )

    val collapsedFullProgress = Bounds(
        start = collapsedContent.start,
        top = collapsedAlbum.bottom,
        width = collapsedContent.width,
        height = 0.dp
    )

    // Expanded state

    val expandedBackground = Bounds(
        start = 0.dp,
        top = 0.dp,
        width = containerSize.width,
        height = containerSize.height
    )

    val expandedContent = Bounds(
        start = startInset,
        top = topInset,
        width = containerSize.width - startInset - endInset,
        height = containerSize.height - topInset - bottomInset
    )

    val toolbar = Bounds(
        start = 0.dp,
        top = 0.dp,
        width = containerSize.width,
        height = toolbarHeight + topInset
    )

    val expandedPlaylist = Bounds(
        start = 0.dp,
        top = toolbar.bottom,
        width = containerSize.width,
        height = containerSize.height - toolbar.bottom
    )

    val expandedMiniProgress = Bounds(
        start = 0.dp,
        top = expandedContent.height - miniProgressHeight,
        width = expandedContent.width,
        height = miniProgressHeight
    )

    val expandedArtworkSize = maxOf(expandedContent.width - 32.dp, expandedContent.height * 0.4F)
    val expandedTitleHeight = expandedTitleSize.estimateLayoutHeight(density)
    val expandedSecondaryHeight = expandedSecondarySize.estimateLayoutHeight(density)
    val expandedArtistHeight = if (hasArtist) expandedSecondaryHeight else 0.dp
    val expandedAlbumHeight = if (hasAlbum) expandedSecondaryHeight else 0.dp
    val expandedTextHeight = expandedTitleHeight + expandedArtistHeight + expandedAlbumHeight
    val expandedVerticalSpacing = (
        expandedContent.height -
            toolbarHeight -
            playlistPeekHeight -
            expandedArtworkSize -
            expandedTextHeight -
            max(expandedPlayPauseSize, expandedOtherControlsSize) -
            expandedProgressSize
        ) / 5

    val expandedArtwork = Bounds(
        start = (expandedContent.width - expandedArtworkSize) / 2,
        top = toolbar.bottom + expandedVerticalSpacing,
        width = expandedArtworkSize,
        height = expandedArtworkSize
    )

    val expandedTextWidth = expandedContent.width - expandedTextHorizontalPadding * 2

    val expandedTitle = Bounds(
        start = expandedTextHorizontalPadding,
        top = expandedArtwork.bottom + expandedVerticalSpacing,
        width = expandedTextWidth,
        height = expandedTitleHeight
    )
    val expandedArtist = Bounds(
        start = expandedTextHorizontalPadding,
        top = expandedTitle.bottom,
        width = expandedTextWidth,
        height = expandedArtistHeight
    )
    val expandedAlbum = Bounds(
        start = expandedTextHorizontalPadding,
        top = expandedArtist.bottom,
        width = expandedTextWidth,
        height = expandedAlbumHeight
    )

    val expandedFullProgress = Bounds(
        start = expandedProgressHorizontalPadding,
        top = expandedAlbum.bottom + expandedVerticalSpacing,
        width = expandedContent.width - expandedProgressHorizontalPadding * 2,
        height = expandedProgressSize
    )

    val expandedPlayPauseTop = expandedFullProgress.bottom + expandedVerticalSpacing
    val expandedOtherControlsTop =
        expandedPlayPauseTop + (expandedPlayPauseSize - expandedOtherControlsSize) / 2
    val expandedControlsWidth = expandedContent.width - expandedControlsHorizontalPadding * 2
    val expandedControlsSize = expandedPlayPauseSize + expandedOtherControlsSize * 4
    val expandedControlsSpacing = min((expandedControlsWidth - expandedControlsSize) / 4, 32.dp)
    val expandedControlsExtraPadding =
        (expandedControlsWidth - expandedControlsSize - expandedControlsSpacing * 4) / 2

    val expandedRepeat = Bounds(
        start = expandedControlsHorizontalPadding + expandedControlsExtraPadding,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedPrevTrack = Bounds(
        start = expandedRepeat.end + expandedControlsSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedPlayPause = Bounds(
        start = expandedPrevTrack.end + expandedControlsSpacing,
        top = expandedPlayPauseTop,
        width = expandedPlayPauseSize,
        height = expandedPlayPauseSize
    )
    val expandedNextTrack = Bounds(
        start = expandedPlayPause.end + expandedControlsSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedShuffle = Bounds(
        start = expandedNextTrack.end + expandedControlsSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )

    val playlistHandleHeight = playlistPeekHeight + bottomInset

    return PlayerGeometry(
        content = ContentLayoutSpec(
            bounds = morph(collapsedBackground, expandedBackground),
            corners = CornerSizes(
                topStart = morph(input.cornerSize, 0.dp, EaseInCubic),
                topEnd = morph(input.cornerSize, 0.dp, EaseInCubic),
                bottomStart = constant(0.dp),
                bottomEnd = constant(0.dp)
            ),
            dragDirection = Orientation.Vertical,
            dragDistance = density.run {
                val collapsedTop = collapsedBackground.top + collapsedContent.top
                val expandedTop = expandedBackground.top + expandedContent.top
                (collapsedTop - expandedTop).toPx()
            }
        ),

        playlist = PlaylistLayoutSpec(
            bounds = morph(collapsedPlaylist, expandedPlaylist),
            alpha = morph(0F, 1F, easing = WindowEasing(0.7F, 1F)),
            cornerSizes = CornerSizes(
                topStart = constant(input.cornerSize),
                topEnd = constant(input.cornerSize),
                bottomStart = constant(0.dp),
                bottomEnd = constant(0.dp)
            ),
            contentAlpha = morph(0f, 1f),
            contentPadding = PaddingValues(
                start = startInset.coerceAtLeast(input.playlistMinSidePadding),
                end = endInset.coerceAtLeast(input.playlistMinSidePadding),
                bottom = bottomInset
            ),
            topSpacing = constant(0.dp),
            handleHeight = playlistHandleHeight,
            handleDragDistance = density.run {
                (expandedPlaylist.height - playlistHandleHeight).toPx()
            }
        ),

        progress = ProgressLayoutSpec(
            full = ControlLayoutSpec(
                bounds = morph(collapsedFullProgress, expandedFullProgress),
                alpha = morph(0F, 1F, WindowEasing(0.7F, 1F))
            ),
            mini = ControlLayoutSpec(
                bounds = morph(collapsedMiniProgress, expandedMiniProgress),
                alpha = morph(1F, 0F, WindowEasing(0.0F, 0.3F))
            )
        ),

        toolbar = ControlLayoutSpec(
            bounds = constant(
                Bounds(
                    start = startInset,
                    top = 0.dp,
                    width = containerSize.width,
                    height = toolbarHeight + topInset
                )
            ),
            alpha = morph(0F, 1F, WindowEasing(0.7F, 0.9F))
        ),

        title = TextLayoutSpec(
            bounds = morph(collapsedTitle, expandedTitle),
            fontSize = morph(collapsedTitleSize, expandedTitleSize)
        ),
        artist = TextLayoutSpec(
            bounds = morph(collapsedArtist, expandedArtist),
            fontSize = morph(collapsedSecondarySize, expandedSecondarySize)
        ),
        album = TextLayoutSpec(
            bounds = morph(collapsedAlbum, expandedAlbum),
            fontSize = morph(0.sp, expandedSecondarySize),
            alpha = morph(0F, 1F, WindowEasing(0.5F, 0.8F))
        ),
        textAlignmentProgress = morph(0F, 0.5F),

        artwork = ArtworkLayoutSpec(
            bounds = morph(
                start = collapsedArtwork,
                end = expandedArtwork,
                startEasing = EaseInOutCubic,
                widthEasing = EaseInCubic,
                heightEasing = EaseInCubic
            ),
            cornerPercentage = morph(20F, 10F)
        ),

        controls = ControlsLayoutSpec(
            repeat = ControlLayoutSpec(
                bounds = morph(collapsedRepeat, expandedRepeat),
                alpha = morph(0F, 1F, WindowEasing(0.5F, 0.8F))
            ),
            prevTrack = ControlLayoutSpec(
                bounds = morph(collapsedPrevTrack, expandedPrevTrack),
                alpha = constant(1F)
            ),
            playPause = ControlLayoutSpec(
                bounds = morph(collapsedPlayPause, expandedPlayPause),
                alpha = constant(1F)
            ),
            nextTrack = ControlLayoutSpec(
                bounds = morph(collapsedNextTrack, expandedNextTrack),
                alpha = constant(1F)
            ),
            shuffle = ControlLayoutSpec(
                bounds = morph(collapsedShuffle, expandedShuffle),
                alpha = morph(0F, 1F, WindowEasing(0.5F, 0.8F))
            )
        ).let {
            if (layoutDirection == LayoutDirection.Rtl) it.withReversedOrder() else it
        }
    )
}

private fun calculateLandscapeGeometry(
    density: Density,
    layoutDirection: LayoutDirection,
    input: PlayerGeometryInput.Landscape,
    insets: PaddingValues,
    hasAlbum: Boolean,
    hasArtist: Boolean,
    collapsedTitleSize: TextUnit,
    expandedTitleSize: TextUnit,
    collapsedSecondaryTextSize: TextUnit,
    expandedSecondaryTextSize: TextUnit,
    expandedArtworkHeightFraction: Float,
    collapsedPlaylistHeightFraction: Float,
    expandedPlaylistWidth: Dp
): PlayerGeometry {
    val containerSize = input.containerSize
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val startInset = insets.calculateStartPadding(layoutDirection)
    val endInset = insets.calculateEndPadding(layoutDirection)

    // Constants
    val toolbarHeight = 64.dp
    val collapsedControlSize = 48.dp
    val collapsedControlSpacing = 8.dp
    val collapsedProgressHeight = 8.dp
    val collapsedProgressBottomSpacing = 8.dp
    val collapsedMinItemSpacing = 16.dp
    val collapsedMinArtworkSidePadding = 16.dp
    val expandedPlayPauseSize = 72.dp
    val expandedOtherControlsSize = 48.dp
    val expandedProgressHeight = 64.dp
    val expandedProgressHorizontalPadding = 8.dp
    val expandedControlsHorizontalPadding = 16.dp
    val expandedTextHorizontalPadding = 8.dp

    // Collapsed state
    val collapsedBackground = Bounds(
        start = containerSize.width - input.collapsedWidth,
        top = 0.dp,
        width = input.collapsedWidth,
        height = containerSize.height
    )

    val collapsedContentHeight = containerSize.height * (1F - collapsedPlaylistHeightFraction)
    val collapsedPlaylist = Bounds(
        start = containerSize.width - input.collapsedWidth,
        top = collapsedContentHeight,
        width = input.collapsedWidth,
        height = containerSize.height * collapsedPlaylistHeightFraction
    )

    val collapsedContent = Bounds(
        start = 0.dp,
        top = topInset,
        width = input.collapsedWidth - endInset,
        height = collapsedContentHeight - topInset -
            (if (collapsedContentHeight != containerSize.height) 0.dp else bottomInset)
    )

    val collapsedTitleHeight = collapsedTitleSize.estimateLayoutHeight(density)
    val collapsedSecondaryTextHeight = collapsedSecondaryTextSize.estimateLayoutHeight(density)
    val collapsedAlbumHeight = if (hasAlbum) collapsedSecondaryTextHeight else 0.dp
    val collapsedArtistHeight = if (hasArtist) collapsedSecondaryTextHeight else 0.dp
    val collapsedTextHeight = collapsedTitleHeight + collapsedAlbumHeight + collapsedArtistHeight

    val collapsedArtworkAvailableHeight = collapsedContent.height -
        collapsedTextHeight -
        collapsedControlSize -
        collapsedProgressHeight -
        collapsedProgressBottomSpacing -
        collapsedMinItemSpacing * 4

    val collapsedArtworkSize = min(
        collapsedContent.width - collapsedMinArtworkSidePadding * 2,
        collapsedArtworkAvailableHeight
    )
    val collapsedItemVertSpacing = (
        collapsedContent.height -
            collapsedArtworkSize -
            collapsedTextHeight -
            collapsedControlSize -
            collapsedProgressHeight -
            collapsedProgressBottomSpacing
        ) / 4

    val collapsedArtwork = Bounds(
        start = (collapsedContent.width - collapsedArtworkSize) / 2F,
        top = collapsedContent.top + collapsedItemVertSpacing,
        width = collapsedArtworkSize,
        height = collapsedArtworkSize
    )

    val collapsedProgress = Bounds(
        start = collapsedContent.start,
        top = collapsedContent.bottom - collapsedProgressHeight - collapsedProgressBottomSpacing,
        width = collapsedContent.width,
        height = collapsedProgressHeight
    )

    val collapsedTextStart = collapsedContent.start
    val collapsedTextWidth = collapsedContent.width

    val collapsedTitle = Bounds(
        start = collapsedTextStart,
        top = collapsedArtwork.bottom + collapsedItemVertSpacing,
        width = collapsedTextWidth,
        height = collapsedTitleHeight
    )
    val collapsedArtist = Bounds(
        start = collapsedTextStart,
        top = collapsedTitle.bottom,
        width = collapsedTextWidth,
        height = collapsedArtistHeight
    )
    val collapsedAlbum = Bounds(
        start = collapsedTextStart,
        top = collapsedArtist.bottom,
        width = collapsedTextWidth,
        height = collapsedAlbumHeight
    )

    val collapsedControlsPadding =
        (collapsedContent.width - collapsedControlSize * 5 - collapsedControlSpacing * 4) / 2
    val collapsedControlsTop = collapsedAlbum.bottom + collapsedItemVertSpacing

    val collapsedRepeat = Bounds(
        start = collapsedContent.start + collapsedControlsPadding,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedPrevTrack = Bounds(
        start = collapsedRepeat.end + collapsedControlSpacing,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedPlayPause = Bounds(
        start = collapsedPrevTrack.end + collapsedControlSpacing,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedNextTrack = Bounds(
        start = collapsedPlayPause.end + collapsedControlSpacing,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedShuffle = Bounds(
        start = collapsedNextTrack.end + collapsedControlSpacing,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )

    // Expanded state
    val expandedBackground = Bounds(
        start = 0.dp,
        top = 0.dp,
        width = containerSize.width,
        height = containerSize.height
    )

    val expandedPlaylist = Bounds(
        start = containerSize.width - expandedPlaylistWidth,
        top = 0.dp,
        width = expandedPlaylistWidth,
        height = containerSize.height
    )

    val expandedContent = Bounds(
        start = startInset,
        top = topInset,
        width = containerSize.width - expandedPlaylist.width - startInset,
        height = containerSize.height - topInset - bottomInset
    )

    val expandedArtworkSize = expandedContent.height * expandedArtworkHeightFraction
    val expandedHeightRemainder = containerSize.height -
        topInset -
        toolbarHeight -
        bottomInset -
        expandedArtworkSize -
        max(expandedPlayPauseSize, expandedOtherControlsSize) -
        expandedProgressHeight
    val expandedVertSpacing = expandedHeightRemainder / 4

    val expandedArtwork = Bounds(
        start = expandedContent.start + 16.dp,
        top = toolbarHeight + topInset + expandedVertSpacing,
        width = expandedArtworkSize,
        height = expandedArtworkSize
    )

    val expandedProgress = Bounds(
        start = expandedContent.start + expandedProgressHorizontalPadding,
        top = expandedArtwork.bottom + expandedVertSpacing,
        width = expandedContent.width - expandedProgressHorizontalPadding * 2,
        height = expandedProgressHeight
    )

    val expandedControlsWidth = expandedContent.width - expandedControlsHorizontalPadding * 2
    val expandedPlayPauseTop = expandedProgress.bottom + expandedVertSpacing
    val expandedOtherControlsTop =
        expandedPlayPauseTop + (expandedPlayPauseSize - expandedOtherControlsSize) / 2
    val expandedControlsSize = expandedPlayPauseSize + expandedOtherControlsSize * 4
    val expandedControlsSpacing = min((expandedControlsWidth - expandedControlsSize) / 4, 48.dp)
    val expandedControlsExtraPadding =
        (expandedControlsWidth - expandedControlsSize - expandedControlsSpacing * 4) / 2

    val expandedRepeat = Bounds(
        start = expandedContent.start +
            expandedControlsHorizontalPadding + expandedControlsExtraPadding,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedPrevTrack = Bounds(
        start = expandedRepeat.end + expandedControlsSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedPlayPause = Bounds(
        start = expandedPrevTrack.end + expandedControlsSpacing,
        top = expandedPlayPauseTop,
        width = expandedPlayPauseSize,
        height = expandedPlayPauseSize
    )
    val expandedNextTrack = Bounds(
        start = expandedPlayPause.end + expandedControlsSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedShuffle = Bounds(
        start = expandedNextTrack.end + expandedControlsSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )

    val expandedTitleHeight = expandedTitleSize.estimateLayoutHeight(density)
    val expandedSecondaryTextHeight = expandedSecondaryTextSize.estimateLayoutHeight(density)
    val expandedAlbumHeight = if (hasAlbum) expandedSecondaryTextHeight else 0.dp
    val expandedArtistHeight = if (hasArtist) expandedSecondaryTextHeight else 0.dp
    val expandedTextHeight = expandedTitleHeight + expandedArtistHeight + expandedAlbumHeight
    val expandedTextSpacing = (expandedArtwork.height - expandedTextHeight) / 2
    val expandedTextStart = expandedArtwork.end + expandedTextHorizontalPadding
    val expandedTextWidth = expandedContent.end - expandedTextStart - expandedTextHorizontalPadding

    val expandedTitle = Bounds(
        start = expandedTextStart,
        top = expandedArtwork.top + expandedTextSpacing,
        width = expandedTextWidth,
        height = expandedTitleHeight
    )
    val expandedArtist = Bounds(
        start = expandedTextStart,
        top = expandedTitle.bottom,
        width = expandedTextWidth,
        height = expandedArtistHeight
    )
    val expandedAlbum = Bounds(
        start = expandedTextStart,
        top = expandedArtist.bottom,
        width = expandedTextWidth,
        height = expandedAlbumHeight
    )

    return PlayerGeometry(
        content = ContentLayoutSpec(
            bounds = morph(collapsedBackground, expandedBackground),
            corners = CornerSizes(
                topStart = morph(input.cornerSize, 0.dp, EaseInQuint),
                topEnd = constant(0.dp),
                bottomStart = morph(input.cornerSize, 0.dp, EaseInQuint),
                bottomEnd = constant(0.dp)
            ),
            dragDirection = Orientation.Horizontal,
            dragDistance = density.run {
                (expandedBackground.width - collapsedBackground.width).toPx()
            }
        ),

        playlist = PlaylistLayoutSpec(
            bounds = morph(
                collapsedPlaylist,
                expandedPlaylist,
                WindowEasing(0.7F, 1F, EaseOutCubic)
            ),
            contentPadding = PaddingValues(
                start = input.playlistMinSidePadding,
                end = endInset.coerceAtLeast(input.playlistMinSidePadding),
                bottom = bottomInset
            ),
            topSpacing = morph(0.dp, topInset),
            alpha = if (collapsedPlaylistHeightFraction > 0) {
                constant(1F)
            } else {
                morph(0F, 1F, WindowEasing(0.8F, 1F))
            },
            cornerSizes = CornerSizes(
                topStart = constant(0.dp),
                topEnd = constant(0.dp),
                bottomStart = if (collapsedPlaylistHeightFraction > 0) {
                    morph(input.cornerSize, 0.dp)
                } else {
                    constant(0.dp)
                },
                bottomEnd = constant(0.dp)
            ),
            contentAlpha = constant(1F)
        ),

        progress = ProgressLayoutSpec(
            full = ControlLayoutSpec(
                bounds = morph(collapsedProgress, expandedProgress),
                alpha = morph(0F, 1F, WindowEasing(0.9F, 1F, EaseInCubic))
            ),
            mini = ControlLayoutSpec(
                bounds = constant(collapsedProgress),
                alpha = morph(1F, 0F, WindowEasing(0.0F, 0.2F))
            )
        ),

        toolbar = ControlLayoutSpec(
            bounds = constant(
                Bounds(
                    start = 0.dp,
                    top = 0.dp,
                    width = containerSize.width - expandedPlaylist.width,
                    height = toolbarHeight + topInset
                )
            ),
            alpha = morph(0F, 1F, WindowEasing(0.7F, 0.9F))
        ),

        artwork = ArtworkLayoutSpec(
            bounds = morph(
                start = collapsedArtwork,
                end = expandedArtwork,
                topEasing = WindowEasing(0.4F, 1F),
                widthEasing = WindowEasing(0.3F, 1F),
                heightEasing = WindowEasing(0.3F, 1F)
            ),
            cornerPercentage = constant(10F)
        ),

        title = TextLayoutSpec(
            bounds = morph(
                collapsedTitle,
                expandedTitle,
                topEasing = EaseInCubic
            ),
            fontSize = morph(collapsedTitleSize, expandedTitleSize)
        ),
        artist = TextLayoutSpec(
            bounds = morph(
                collapsedArtist,
                expandedArtist,
                topEasing = EaseInCubic
            ),
            fontSize = morph(collapsedSecondaryTextSize, expandedSecondaryTextSize)
        ),
        album = TextLayoutSpec(
            bounds = morph(
                collapsedAlbum,
                expandedAlbum,
                topEasing = EaseInCubic
            ),
            fontSize = morph(collapsedSecondaryTextSize, expandedSecondaryTextSize)
        ),

        textAlignmentProgress = constant(0.5F), // always center aligned

        controls = ControlsLayoutSpec(
            repeat = ControlLayoutSpec(
                bounds = morph(
                    collapsedRepeat,
                    expandedRepeat,
                    startEasing = EaseInCubic,
                    topEasing = EaseInCubic
                ),
                alpha = constant(1F)
            ),
            prevTrack = ControlLayoutSpec(
                bounds = morph(
                    collapsedPrevTrack,
                    expandedPrevTrack,
                    startEasing = EaseInCubic,
                    topEasing = EaseInCubic
                ),
                alpha = constant(1F)
            ),
            playPause = ControlLayoutSpec(
                bounds = morph(
                    collapsedPlayPause,
                    expandedPlayPause,
                    startEasing = EaseInCubic,
                    topEasing = EaseInCubic
                ),
                alpha = constant(1F)
            ),
            nextTrack = ControlLayoutSpec(
                bounds = morph(
                    collapsedNextTrack,
                    expandedNextTrack,
                    startEasing = EaseInCubic,
                    topEasing = EaseInCubic
                ),
                alpha = constant(1F)
            ),
            shuffle = ControlLayoutSpec(
                bounds = morph(
                    collapsedShuffle,
                    expandedShuffle,
                    startEasing = EaseInCubic,
                    topEasing = EaseInCubic
                ),
                alpha = constant(1F)
            )
        ).let {
            if (layoutDirection == LayoutDirection.Rtl) it.withReversedOrder() else it
        }
    )
}

private fun calculateFoldablePaneGeometry(
    density: Density,
    layoutDirection: LayoutDirection,
    input: PlayerGeometryInput.Foldable,
    insets: PaddingValues,
    hasAlbum: Boolean,
    hasArtist: Boolean
): PlayerGeometry {
    val containerSize = input.containerSize
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val startInset = insets.calculateStartPadding(layoutDirection)
    val endInset = insets.calculateEndPadding(layoutDirection)

    // Constants
    val toolbarHeight = 64.dp
    val progressSize = 64.dp
    val playPauseSize = 72.dp
    val otherControlsSize = 48.dp
    val textHorizontalPadding = 8.dp
    val controlsHorizontalPadding = 16.dp
    val progressHorizontalPadding = 16.dp
    val playlistPeekHeight = 48.dp
    val titleSize = 24.sp
    val secondarySize = 18.sp

    val background = Bounds(
        start = containerSize.width - input.paneWidth,
        top = 0.dp,
        width = input.paneWidth,
        height = containerSize.height
    )

    val content = Bounds(
        start = containerSize.width - input.paneWidth,
        top = topInset,
        width = containerSize.width - input.paneWidth - endInset,
        height = containerSize.height - topInset - bottomInset
    )

    val toolbar = Bounds(
        start = 0.dp,
        top = 0.dp,
        width = background.width,
        height = toolbarHeight + topInset
    )

    val playlist = Bounds(
        start = background.start,
        top = toolbar.bottom,
        width = background.width,
        height = containerSize.height - toolbar.bottom
    )

    val artworkSize = maxOf(content.width - 32.dp, content.height * 0.4F)
    val titleHeight = titleSize.estimateLayoutHeight(density)
    val secondaryHeight = secondarySize.estimateLayoutHeight(density)
    val artistHeight = if (hasArtist) secondaryHeight else 0.dp
    val albumHeight = if (hasAlbum) secondaryHeight else 0.dp
    val textHeight = titleHeight + artistHeight + albumHeight
    val verticalSpacing = (
        content.height -
            toolbarHeight -
            playlistPeekHeight -
            artworkSize -
            textHeight -
            max(playPauseSize, otherControlsSize) -
            progressSize
        ) / 5

    val artwork = Bounds(
        start = (content.width - artworkSize) / 2,
        top = toolbar.bottom + verticalSpacing,
        width = artworkSize,
        height = artworkSize
    )

    val textWidth = content.width - textHorizontalPadding * 2

    val title = Bounds(
        start = textHorizontalPadding,
        top = artwork.bottom + verticalSpacing,
        width = textWidth,
        height = titleHeight
    )
    val artist = Bounds(
        start = textHorizontalPadding,
        top = title.bottom,
        width = textWidth,
        height = artistHeight
    )
    val album = Bounds(
        start = textHorizontalPadding,
        top = artist.bottom,
        width = textWidth,
        height = albumHeight
    )

    val progress = Bounds(
        start = progressHorizontalPadding,
        top = album.bottom + verticalSpacing,
        width = content.width - progressHorizontalPadding * 2,
        height = progressSize
    )

    val playPauseTop = progress.bottom + verticalSpacing
    val otherControlsTop = playPauseTop + (playPauseSize - otherControlsSize) / 2
    val controlsWidth = content.width - controlsHorizontalPadding * 2
    val controlsSize = playPauseSize + otherControlsSize * 4
    val controlsSpacing = min((controlsWidth - controlsSize) / 4, 32.dp)
    val controlsExtraPadding = (controlsWidth - controlsSize - controlsSpacing * 4) / 2

    val repeat = Bounds(
        start = controlsHorizontalPadding + controlsExtraPadding,
        top = otherControlsTop,
        width = otherControlsSize,
        height = otherControlsSize
    )
    val prevTrack = Bounds(
        start = repeat.end + controlsSpacing,
        top = otherControlsTop,
        width = otherControlsSize,
        height = otherControlsSize
    )
    val playPause = Bounds(
        start = prevTrack.end + controlsSpacing,
        top = playPauseTop,
        width = playPauseSize,
        height = playPauseSize
    )
    val nextTrack = Bounds(
        start = playPause.end + controlsSpacing,
        top = otherControlsTop,
        width = otherControlsSize,
        height = otherControlsSize
    )
    val shuffle = Bounds(
        start = nextTrack.end + controlsSpacing,
        top = otherControlsTop,
        width = otherControlsSize,
        height = otherControlsSize
    )

    val playlistHandleHeight = playlistPeekHeight + bottomInset

    return PlayerGeometry(
        content = ContentLayoutSpec(
            bounds = constant(background),
            corners = CornerSizes(
                topStart = constant(input.cornerSize),
                topEnd = constant(0.dp),
                bottomStart = constant(input.cornerSize),
                bottomEnd = constant(0.dp)
            ),
            dragDirection = null,
            dragDistance = 0F
        ),

        playlist = PlaylistLayoutSpec(
            bounds = constant(playlist),
            alpha = constant(1F),
            cornerSizes = CornerSizes(
                topStart = constant(input.cornerSize),
                topEnd = constant(input.cornerSize),
                bottomStart = constant(0.dp),
                bottomEnd = constant(0.dp)
            ),
            contentAlpha = morph(0F, 1F),
            contentPadding = PaddingValues(
                start = startInset.coerceAtLeast(input.playlistMinSidePadding),
                end = endInset.coerceAtLeast(input.playlistMinSidePadding),
                bottom = bottomInset
            ),
            topSpacing = constant(0.dp),
            handleHeight = playlistHandleHeight,
            handleDragDistance = density.run {
                (playlist.height - playlistHandleHeight).toPx()
            }
        ),

        progress = ProgressLayoutSpec(
            full = ControlLayoutSpec(
                bounds = constant(progress),
                alpha = constant(1F)
            ),
            mini = ControlLayoutSpec(
                bounds = constant(Bounds(0.dp, 0.dp, 0.dp, 0.dp)),
                alpha = constant(0F)
            )
        ),

        toolbar = ControlLayoutSpec(
            bounds = constant(toolbar),
            alpha = constant(1F)
        ),

        title = TextLayoutSpec(
            bounds = constant(title),
            fontSize = constant(titleSize)
        ),
        artist = TextLayoutSpec(
            bounds = constant(artist),
            fontSize = constant(secondarySize)
        ),
        album = TextLayoutSpec(
            bounds = constant(album),
            fontSize = constant(secondarySize)
        ),
        textAlignmentProgress = constant(0.5F),

        artwork = ArtworkLayoutSpec(
            bounds = constant(artwork),
            cornerPercentage = constant(10F)
        ),

        controls = ControlsLayoutSpec(
            repeat = ControlLayoutSpec(
                bounds = constant(repeat),
                alpha = constant(1F)
            ),
            prevTrack = ControlLayoutSpec(
                bounds = constant(prevTrack),
                alpha = constant(1F)
            ),
            playPause = ControlLayoutSpec(
                bounds = constant(playPause),
                alpha = constant(1F)
            ),
            nextTrack = ControlLayoutSpec(
                bounds = constant(nextTrack),
                alpha = constant(1F)
            ),
            shuffle = ControlLayoutSpec(
                bounds = constant(shuffle),
                alpha = constant(1F)
            )
        ).let {
            if (layoutDirection == LayoutDirection.Rtl) it.withReversedOrder() else it
        }
    )
}

private fun TextUnit.estimateLayoutHeight(density: Density) = with(density) {
    toDp() * 1.3F
}

internal fun MainLayout.toGeometryInput(): PlayerGeometryInput = when (this) {
    is MainLayout.Portrait -> PlayerGeometryInput.Portrait(
        containerSize = containerSize,
        cornerSize = nowPlayingCornerSize,
        playlistMinSidePadding = listContentMinSidePadding,
        collapsedHeight = collapsedNowPlayingHeight
    )

    is MainLayout.Landscape -> PlayerGeometryInput.Landscape(
        containerSize = containerSize,
        cornerSize = nowPlayingCornerSize,
        playlistMinSidePadding = listContentMinSidePadding,
        collapsedWidth = collapsedNowPlayingWidth
    )

    is MainLayout.Foldable -> PlayerGeometryInput.Foldable(
        containerSize = containerSize,
        cornerSize = nowPlayingCornerSize,
        playlistMinSidePadding = listContentMinSidePadding,
        paneWidth = nowPlayingWidth
    )
}

@Composable
internal fun rememberPlayerGeometry(
    input: PlayerGeometryInput,
    insets: PaddingValues,
    hasArtist: Boolean,
    hasAlbum: Boolean
): PlayerGeometry {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(input, density, layoutDirection, insets, hasArtist, hasAlbum) {
        calculatePlayerGeometry(input, density, layoutDirection, insets, hasArtist, hasAlbum)
    }
}
