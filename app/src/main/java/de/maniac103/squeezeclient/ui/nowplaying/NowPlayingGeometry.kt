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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import de.maniac103.squeezeclient.ui.maincontent.MainLayout

@Immutable
internal sealed interface PlayerGeometryInput {
    val containerSize: DpSize
    val cornerSize: Dp

    data class Portrait(
        override val containerSize: DpSize,
        override val cornerSize: Dp,
        val collapsedHeight: Dp
    ) : PlayerGeometryInput

    data class Landscape(
        override val containerSize: DpSize,
        override val cornerSize: Dp,
        val collapsedWidth: Dp
    ) : PlayerGeometryInput
}

@Immutable
internal data class PlaylistLayoutSpec(
    // driven by now playing progress
    val bounds: Morph<Bounds>,
    val alpha: Morph<Float>,
    val nowPlayingCornerSizes: CornerSizes,
    // driven by playlist progress
    val playlistCornerSizes: CornerSizes,
    val contentAlpha: Morph<Float>,
    // static
    val padding: PaddingValues,
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
    val dragDirection: Orientation,
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
            collapsedArtworkHeightFraction = if (isLarge) 0.2F else 0.3F,
            expandedArtworkHeightFraction = if (isLarge) 0.5F else 0.35F,
            collapsedPlaylistHeightFraction = if (isLarge) 0.5F else 0.0F,
            expandedPlaylistWidthFraction = if (isLarge) 0.3F else 0.35F
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
    val collapsedArtworkSize = 48.dp
    val toolbarHeight = 64.dp
    val miniProgressHeight = 4.dp
    val collapsedControlSize = 48.dp
    val collapsedControlSpacing = 4.dp
    val expandedProgressSize = 64.dp
    val expandedPlayPauseSize = 72.dp
    val expandedOtherControlsSize = 48.dp
    val collapsedTitleSize = 16.sp
    val collapsedSecondarySize = 14.sp
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
        start = 8.dp,
        top = (collapsedMiniProgress.top - collapsedArtworkSize) / 2,
        width = collapsedArtworkSize,
        height = collapsedArtworkSize
    )

    val collapsedControlsWidth = collapsedControlSize * 3 + collapsedControlSpacing * 2
    val collapsedControlsStart = collapsedContent.width - 8.dp - collapsedControlsWidth
    val collapsedControlsTop = (collapsedMiniProgress.top - collapsedControlSize) / 2

    val collapsedRepeat = Bounds(
        start = collapsedControlsStart,
        top = collapsedControlsTop,
        width = 0.dp,
        height = collapsedControlSize
    )
    val collapsedPrevTrack = Bounds(
        start = collapsedControlsStart,
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
        start = collapsedNextTrack.end,
        top = collapsedControlsTop,
        width = 0.dp,
        height = collapsedControlSize
    )

    val collapsedTextWidth = collapsedControlsStart - collapsedArtwork.end - 16.dp
    val collapsedTextStart = collapsedArtwork.end + 8.dp
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

    val expandedArtworkSize = minOf(expandedContent.width, expandedContent.height * 0.4F)
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

    val expandedTextStart = 8.dp
    val expandedTextWidth = expandedContent.width - 16.dp

    val expandedTitle = Bounds(
        start = expandedTextStart,
        top = expandedArtwork.bottom + expandedVerticalSpacing,
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

    val expandedFullProgress = Bounds(
        start = 8.dp,
        top = expandedAlbum.bottom + expandedVerticalSpacing,
        width = expandedContent.width - 16.dp,
        height = expandedProgressSize
    )

    val expandedControlsPadding = 16.dp
    val expandedPlayPauseTop = expandedFullProgress.bottom + expandedVerticalSpacing
    val expandedOtherControlsTop =
        expandedPlayPauseTop + (expandedPlayPauseSize - expandedOtherControlsSize) / 2
    val expandedControlsWidth = expandedContent.width - expandedControlsPadding * 2
    val expandedControlsSpacing =
        (expandedControlsWidth - expandedPlayPauseSize - expandedOtherControlsSize * 4) / 4

    val expandedRepeat = Bounds(
        start = expandedControlsPadding,
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
                topStart = morph(input.cornerSize, 0.dp),
                topEnd = morph(input.cornerSize, 0.dp),
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
            nowPlayingCornerSizes = CornerSizes(
                topStart = constant(0.dp),
                topEnd = constant(0.dp),
                bottomStart = constant(0.dp),
                bottomEnd = constant(0.dp)
            ),
            playlistCornerSizes = CornerSizes(
                topStart = morph(input.cornerSize, 0.dp),
                topEnd = morph(input.cornerSize, 0.dp),
                bottomStart = constant(0.dp),
                bottomEnd = constant(0.dp)
            ),
            contentAlpha = morph(0f, 1f),
            padding = PaddingValues(start = startInset, end = endInset, bottom = bottomInset),
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
                    height = 64.dp + topInset
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
    collapsedArtworkHeightFraction: Float,
    expandedArtworkHeightFraction: Float,
    collapsedPlaylistHeightFraction: Float,
    expandedPlaylistWidthFraction: Float
): PlayerGeometry {
    val containerSize = input.containerSize
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val startInset = insets.calculateStartPadding(layoutDirection)
    val endInset = insets.calculateEndPadding(layoutDirection)

    // Constants
    val toolbarHeight = 64.dp
    val collapsedControlSize = 48.dp
    val collapsedControlSpacing = 4.dp
    val collapsedProgressHeight = 8.dp
    val expandedPlayPauseSize = 72.dp
    val expandedOtherControlsSize = 48.dp
    val expandedProgressHeight = 64.dp

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
        height = collapsedContentHeight - topInset - bottomInset
    )

    val collapsedTitleHeight = collapsedTitleSize.estimateLayoutHeight(density)
    val collapsedSecondaryTextHeight = collapsedSecondaryTextSize.estimateLayoutHeight(density)
    val collapsedAlbumHeight = if (hasAlbum) collapsedSecondaryTextHeight else 0.dp
    val collapsedArtistHeight = if (hasArtist) collapsedSecondaryTextHeight else 0.dp
    val collapsedTextHeight = collapsedTitleHeight + collapsedAlbumHeight + collapsedArtistHeight

    val collapsedArtworkSize = collapsedContent.height * collapsedArtworkHeightFraction
    val collapsedItemVertSpacing = (
        collapsedContent.height -
            collapsedArtworkSize -
            collapsedTextHeight -
            collapsedControlSize -
            collapsedProgressHeight
        ) / 4

    val collapsedArtwork = Bounds(
        start = (collapsedContent.width - collapsedArtworkSize) / 2F,
        top = collapsedContent.top + collapsedItemVertSpacing,
        width = collapsedArtworkSize,
        height = collapsedArtworkSize
    )

    val collapsedProgress = Bounds(
        start = collapsedContent.start,
        top = collapsedContent.bottom - 16.dp,
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
        start = containerSize.width * (1F - expandedPlaylistWidthFraction),
        top = 0.dp,
        width = containerSize.width * expandedPlaylistWidthFraction,
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
        start = expandedContent.start + 8.dp,
        top = expandedArtwork.bottom + expandedVertSpacing,
        width = expandedContent.width - 16.dp,
        height = expandedProgressHeight
    )

    val expandedControlsPadding = 16.dp
    val expandedControlsWidth = expandedContent.width - expandedControlsPadding * 2
    val expandedPlayPauseTop = expandedProgress.bottom + expandedVertSpacing
    val expandedOtherControlsTop =
        expandedPlayPauseTop + (expandedPlayPauseSize - expandedOtherControlsSize) / 2
    val expandedControlsHorzSpacing =
        (expandedControlsWidth - expandedPlayPauseSize - expandedOtherControlsSize * 4) / 4

    val expandedRepeat = Bounds(
        start = expandedContent.start + expandedControlsPadding,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedPrevTrack = Bounds(
        start = expandedRepeat.end + expandedControlsHorzSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedPlayPause = Bounds(
        start = expandedPrevTrack.end + expandedControlsHorzSpacing,
        top = expandedPlayPauseTop,
        width = expandedPlayPauseSize,
        height = expandedPlayPauseSize
    )
    val expandedNextTrack = Bounds(
        start = expandedPlayPause.end + expandedControlsHorzSpacing,
        top = expandedOtherControlsTop,
        width = expandedOtherControlsSize,
        height = expandedOtherControlsSize
    )
    val expandedShuffle = Bounds(
        start = expandedNextTrack.end + expandedControlsHorzSpacing,
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
    val expandedTextStart = expandedArtwork.end + 8.dp
    val expandedTextWidth = expandedContent.end - expandedTextStart - 8.dp

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
                topStart = morph(input.cornerSize, 0.dp),
                topEnd = constant(0.dp),
                bottomStart = morph(input.cornerSize, 0.dp),
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
            padding = PaddingValues(
                top = topInset,
                end = endInset,
                bottom = bottomInset
            ),
            alpha = if (collapsedPlaylistHeightFraction > 0) {
                constant(1F)
            } else {
                morph(0F, 1F, WindowEasing(0.8F, 1F))
            },
            nowPlayingCornerSizes = CornerSizes(
                topStart = constant(0.dp),
                topEnd = constant(0.dp),
                bottomStart = if (collapsedPlaylistHeightFraction > 0) {
                    morph(input.cornerSize, 0.dp)
                } else {
                    constant(0.dp)
                },
                bottomEnd = constant(0.dp)
            ),
            playlistCornerSizes = CornerSizes(
                topStart = constant(0.dp),
                topEnd = constant(0.dp),
                bottomStart = constant(0.dp),
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
                    height = 64.dp + topInset
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

private fun TextUnit.estimateLayoutHeight(density: Density) = with(density) {
    toDp() * 1.3F
}

internal fun MainLayout.toGeometryInput(): PlayerGeometryInput = when (this) {
    is MainLayout.Portrait ->
        PlayerGeometryInput.Portrait(containerSize, nowPlayingCornerSize, collapsedNowPlayingHeight)

    is MainLayout.Landscape ->
        PlayerGeometryInput.Landscape(containerSize, nowPlayingCornerSize, collapsedNowPlayingWidth)
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
