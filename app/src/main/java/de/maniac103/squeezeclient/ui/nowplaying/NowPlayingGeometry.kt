package de.maniac103.squeezeclient.ui.nowplaying

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import de.maniac103.squeezeclient.ui.maincontent.MainLayout

@Immutable
internal data class Bounds(val left: Dp, val top: Dp, val width: Dp, val height: Dp) {
    val right get() = left + width
    val bottom get() = top + height
    val center get() = DpOffset(left + width / 2, top + height / 2)
}

@Immutable
internal data class PlaylistLayoutSpec(
    val bounds: Bounds,
    val peekHeight: Dp,
    val padding: PaddingValues,
    val alpha: Float
) {
    val needsHandle get() = peekHeight.isSpecified
}

@Immutable
internal data class ControlLayoutSpec(val bounds: Bounds, val alpha: Float)

@Immutable
internal data class TextLayoutSpec(
    val bounds: Bounds,
    val fontSize: TextUnit,
    val alpha: Float = 1F
)

@Immutable
internal data class ProgressLayoutSpec(val mini: ControlLayoutSpec, val full: ControlLayoutSpec)

@Immutable
internal data class CornerSizes(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
)

@Immutable
internal data class ContentLayoutSpec(
    val bounds: Bounds,
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
)

@Immutable
internal data class PlayerGeometry(
    val content: ContentLayoutSpec,
    val playlist: PlaylistLayoutSpec,
    val progress: ProgressLayoutSpec,
    val toolbar: ControlLayoutSpec,

    val artwork: Bounds,
    val controls: ControlsLayoutSpec,

    val title: TextLayoutSpec,
    val artist: TextLayoutSpec,
    val album: TextLayoutSpec,
    val textAlignmentProgress: Float
)

@Composable
internal fun calculatePlayerGeometry(
    layout: MainLayout,
    insets: PaddingValues,
    hasArtist: Boolean,
    hasAlbum: Boolean,
    progress: Float
): PlayerGeometry {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val startInset = insets.calculateStartPadding(layoutDirection)
    val endInset = insets.calculateEndPadding(layoutDirection)

    return when (layout) {
        is MainLayout.Portrait ->
            calculatePortraitGeometry(
                density = density,
                width = layout.containerSize.width,
                height = layout.containerSize.height,
                topInset = topInset,
                bottomInset = bottomInset,
                startInset = startInset,
                endInset = endInset,
                progress = progress,
                hasAlbum = hasAlbum,
                hasArtist = hasArtist,
                collapsedContentHeight = layout.collapsedNowPlayingHeight,
                playlistPeekHeight = 32.dp
            )

        is MainLayout.Landscape -> {
            val isLarge = layout.containerSize.height >= 480.dp
            calculateLandscapeGeometry(
                density = density,
                width = layout.containerSize.width,
                height = layout.containerSize.height,
                topInset = topInset,
                bottomInset = bottomInset,
                startInset = startInset,
                endInset = endInset,
                progress = progress,
                paneWidth = layout.collapsedNowPlayingWidth,
                hasAlbum = hasAlbum,
                hasArtist = hasArtist,
                collapsedTitleSize = 16.sp,
                expandedTitleSize = if (isLarge) 20.sp else 18.sp,
                secondaryTextSize = 16.sp,
                collapsedArtworkHeightFraction = if (isLarge) 0.2F else 0.3F,
                expandedArtworkHeightFraction = if (isLarge) 0.5F else 0.3F,
                collapsedPlaylistHeightFraction = if (isLarge) 0.5F else 0.0F,
                expandedPlaylistWidthFraction = if (isLarge) 0.3F else 0.4F
            )
        }
    }
}

private fun calculatePortraitGeometry(
    density: Density,
    width: Dp,
    height: Dp,
    topInset: Dp,
    bottomInset: Dp,
    startInset: Dp,
    endInset: Dp,
    progress: Float,
    hasAlbum: Boolean,
    hasArtist: Boolean,
    collapsedContentHeight: Dp,
    playlistPeekHeight: Dp
): PlayerGeometry {
    // Constants
    val collapsedArtworkSize = 48.dp
    val toolbarHeight = 64.dp
    val miniProgressHeight = 4.dp
    val collapsedControlSize = 48.dp
    val expandedProgressSize = 64.dp
    val expandedControlsSize = 72.dp
    val collapsedTitleSize = 16.sp
    val collapsedSecondarySize = 14.sp
    val expandedTitleSize = 24.sp
    val expandedSecondarySize = 18.sp

    // Collapsed state

    val collapsedBackground = Bounds(
        left = 0.dp,
        top = height - collapsedContentHeight - bottomInset,
        width = width,
        height = collapsedContentHeight + bottomInset
    )

    val collapsedContent = Bounds(
        left = startInset,
        top = 0.dp,
        width = width - startInset - endInset,
        height = collapsedContentHeight
    )

    val collapsedPlaylist = Bounds(
        left = 0.dp,
        top = collapsedContent.bottom,
        width = width,
        height = height
    )

    val collapsedMiniProgress = Bounds(
        left = 0.dp,
        top = collapsedContent.height - miniProgressHeight,
        width = collapsedContent.width,
        height = miniProgressHeight
    )

    val collapsedArtwork = Bounds(
        left = 8.dp,
        top = (collapsedContentHeight - collapsedArtworkSize - collapsedMiniProgress.height) / 2,
        width = collapsedArtworkSize,
        height = collapsedArtworkSize
    )

    val collapsedControlsWidth = collapsedControlSize * 3 // prev, play/pause, next
    val collapsedControlsLeft = collapsedContent.width - 8.dp - collapsedControlsWidth
    val collapsedControlsTop = (collapsedContentHeight - collapsedControlSize) / 2

    val collapsedRepeat = Bounds(
        left = collapsedControlsLeft,
        top = collapsedControlsTop,
        width = 0.dp,
        height = collapsedControlSize
    )
    val collapsedPrevTrack = Bounds(
        left = collapsedControlsLeft,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedPlayPause = Bounds(
        left = collapsedPrevTrack.right,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedNextTrack = Bounds(
        left = collapsedPlayPause.right,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedShuffle = Bounds(
        left = collapsedNextTrack.right,
        top = collapsedControlsTop,
        width = 0.dp,
        height = collapsedControlSize
    )

    val collapsedTextWidth = collapsedControlsLeft - collapsedArtwork.right - 16.dp
    val collapsedTextLeft = collapsedArtwork.right + 8.dp
    val collapsedTitleHeight = collapsedTitleSize.estimateLayoutHeight(density)
    val collapsedSecondaryHeight = collapsedSecondarySize.estimateLayoutHeight(density)
    val collapsedArtistHeight = if (hasArtist) collapsedSecondaryHeight else 0.dp
    val collapsedTextSpacing =
        (collapsedArtwork.height - collapsedTitleHeight - collapsedArtistHeight) / 2
    val collapsedTitle = Bounds(
        left = collapsedTextLeft,
        top = collapsedArtwork.top + collapsedTextSpacing,
        width = collapsedTextWidth,
        height = collapsedTitleHeight
    )
    val collapsedArtist = Bounds(
        left = collapsedTextLeft,
        top = collapsedTitle.bottom,
        width = collapsedTextWidth,
        height = collapsedArtistHeight
    )
    val collapsedAlbum = Bounds(
        left = collapsedTextLeft,
        top = collapsedArtist.bottom,
        width = collapsedTextWidth,
        height = 0.dp
    )

    val collapsedFullProgress = Bounds(
        left = collapsedContent.left,
        top = collapsedAlbum.bottom,
        width = collapsedContent.width,
        height = 0.dp
    )

    // Expanded state

    val expandedBackground = Bounds(
        left = 0.dp,
        top = 0.dp,
        width = width,
        height = height
    )

    val expandedContent = Bounds(
        left = startInset,
        top = topInset,
        width = width - startInset - endInset,
        height = height - topInset - bottomInset
    )

    val toolbar = Bounds(
        left = 0.dp,
        top = 0.dp,
        width = width,
        height = toolbarHeight + topInset
    )

    val expandedPlaylist = Bounds(
        left = 0.dp,
        top = toolbar.bottom,
        width = width,
        height = height - toolbar.bottom
    )

    val expandedMiniProgress = Bounds(
        left = 0.dp,
        top = expandedContent.height - miniProgressHeight,
        width = expandedContent.width,
        height = miniProgressHeight
    )

    val expandedArtworkSize = minOf(expandedContent.width, expandedContent.height * 0.4F)
    val expandedArtwork = Bounds(
        left = (expandedContent.width - expandedArtworkSize) / 2,
        top = toolbar.bottom + 32.dp,
        width = expandedArtworkSize,
        height = expandedArtworkSize
    )

    val expandedTitleHeight = expandedTitleSize.estimateLayoutHeight(density)
    val expandedSecondaryHeight = expandedSecondarySize.estimateLayoutHeight(density)
    val expandedArtistHeight = if (hasArtist) expandedSecondaryHeight else 0.dp
    val expandedAlbumHeight = if (hasAlbum) expandedSecondaryHeight else 0.dp
    val expandedTextHeight = expandedTitleHeight + expandedArtistHeight + expandedAlbumHeight
    val expandedVerticalSpacing = (
        expandedContent.height -
            expandedArtwork.bottom -
            expandedTextHeight -
            expandedControlsSize -
            expandedProgressSize
        ) / 4

    val expandedTextLeft = 8.dp
    val expandedTextWidth = expandedContent.width - 16.dp

    val expandedTitle = Bounds(
        left = expandedTextLeft,
        top = expandedArtwork.bottom + expandedVerticalSpacing,
        width = expandedTextWidth,
        height = expandedTitleHeight
    )
    val expandedArtist = Bounds(
        left = expandedTextLeft,
        top = expandedTitle.bottom,
        width = expandedTextWidth,
        height = expandedArtistHeight
    )
    val expandedAlbum = Bounds(
        left = expandedTextLeft,
        top = expandedArtist.bottom,
        width = expandedTextWidth,
        height = expandedAlbumHeight
    )

    val expandedFullProgress = Bounds(
        left = 8.dp,
        top = expandedAlbum.bottom + expandedVerticalSpacing,
        width = expandedContent.width - 16.dp,
        height = expandedProgressSize
    )

    val expandedControlsPadding = 16.dp
    val expandedControlsTop = expandedFullProgress.bottom + expandedVerticalSpacing
    val expandedControlsWidth = expandedContent.width - expandedControlsPadding * 2
    val expandedControlsSpacing = (expandedControlsWidth - expandedControlsSize * 5) / 4

    val expandedRepeat = Bounds(
        left = expandedControlsPadding,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedPrevTrack = Bounds(
        left = expandedRepeat.right + expandedControlsSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedPlayPause = Bounds(
        left = expandedPrevTrack.right + expandedControlsSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedNextTrack = Bounds(
        left = expandedPlayPause.right + expandedControlsSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedShuffle = Bounds(
        left = expandedNextTrack.right + expandedControlsSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )

    val contentCornerRadius = lerp(16.dp, 0.dp, progress)

    return PlayerGeometry(
        content = ContentLayoutSpec(
            bounds = lerp(collapsedBackground, expandedBackground, progress),
            corners = CornerSizes(
                topStart = contentCornerRadius,
                topEnd = contentCornerRadius,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            dragDirection = Orientation.Vertical,
            dragDistance = density.run {
                val collapsedTop = collapsedBackground.top + collapsedContent.top
                val expandedTop = expandedBackground.top + expandedContent.top
                (collapsedTop - expandedTop).toPx()
            }
        ),

        playlist = PlaylistLayoutSpec(
            bounds = lerp(collapsedPlaylist, expandedPlaylist, progress),
            padding = PaddingValues(
                start = startInset,
                end = endInset,
                bottom = bottomInset
            ),
            peekHeight = playlistPeekHeight + bottomInset,
            alpha = fadeInWindow(progress, 0.7F, 1F)
        ),

        progress = ProgressLayoutSpec(
            full = ControlLayoutSpec(
                bounds = lerp(collapsedFullProgress, expandedFullProgress, progress),
                alpha = fadeInWindow(progress, 0.7F, 1F)
            ),
            mini = ControlLayoutSpec(
                bounds = lerp(collapsedMiniProgress, expandedMiniProgress, progress),
                alpha = fadeOutWindow(progress, 0.0F, 0.3F)
            )
        ),

        toolbar = ControlLayoutSpec(
            bounds = Bounds(
                left = startInset,
                top = 0.dp,
                width = width,
                height = 64.dp + topInset
            ),
            alpha = fadeInWindow(progress, .50f, .70f)
        ),

        title = TextLayoutSpec(
            bounds = lerp(collapsedTitle, expandedTitle, progress),
            fontSize = lerp(collapsedTitleSize, expandedTitleSize, progress)
        ),
        artist = TextLayoutSpec(
            bounds = lerp(collapsedArtist, expandedArtist, progress),
            fontSize = lerp(collapsedSecondarySize, expandedSecondarySize, progress)
        ),
        album = TextLayoutSpec(
            bounds = lerp(collapsedAlbum, expandedAlbum, progress),
            fontSize = lerp(0.sp, expandedSecondarySize, progress),
            alpha = fadeInWindow(progress, 0.5F, 0.8F)
        ),
        textAlignmentProgress = lerp(0F, 0.5F, progress),

        artwork = lerp(collapsedArtwork, expandedArtwork, progress),

        controls = ControlsLayoutSpec(
            repeat = ControlLayoutSpec(
                bounds = lerp(collapsedRepeat, expandedRepeat, progress),
                alpha = fadeInWindow(progress, 0.5F, 0.8F)
            ),
            prevTrack = ControlLayoutSpec(
                bounds = lerp(collapsedPrevTrack, expandedPrevTrack, progress),
                alpha = 1F
            ),
            playPause = ControlLayoutSpec(
                bounds = lerp(collapsedPlayPause, expandedPlayPause, progress),
                alpha = 1F
            ),
            nextTrack = ControlLayoutSpec(
                bounds = lerp(collapsedNextTrack, expandedNextTrack, progress),
                alpha = 1F
            ),
            shuffle = ControlLayoutSpec(
                bounds = lerp(collapsedShuffle, expandedShuffle, progress),
                alpha = fadeInWindow(progress, 0.5F, 0.8F)
            )
        )
    )
}

private fun calculateLandscapeGeometry(
    density: Density,
    width: Dp,
    height: Dp,
    topInset: Dp,
    bottomInset: Dp,
    startInset: Dp,
    endInset: Dp,
    progress: Float,
    paneWidth: Dp,
    hasAlbum: Boolean,
    hasArtist: Boolean,
    collapsedTitleSize: TextUnit,
    expandedTitleSize: TextUnit,
    secondaryTextSize: TextUnit,
    collapsedArtworkHeightFraction: Float,
    expandedArtworkHeightFraction: Float,
    collapsedPlaylistHeightFraction: Float,
    expandedPlaylistWidthFraction: Float
): PlayerGeometry {
    // Constants
    val toolbarHeight = 64.dp
    val collapsedControlSize = 48.dp
    val collapsedProgressHeight = 8.dp
    val expandedControlsSize = 72.dp
    val expandedProgressHeight = 64.dp
    val expandedArtworkControlsPadding = 16.dp

    // Collapsed state
    val collapsedBackground = Bounds(
        left = width - paneWidth,
        top = 0.dp,
        width = paneWidth,
        height = height
    )

    val collapsedPlaylist = Bounds(
        left = width - paneWidth,
        top = height * (1F - collapsedPlaylistHeightFraction),
        width = paneWidth,
        height = height * collapsedPlaylistHeightFraction
    )

    val collapsedContent = Bounds(
        left = 0.dp,
        top = topInset,
        width = paneWidth - endInset,
        height = height * (1F - collapsedPlaylistHeightFraction) - topInset - bottomInset
    )

    val collapsedTitleHeight = collapsedTitleSize.estimateLayoutHeight(density)
    val secondaryTextHeight = secondaryTextSize.estimateLayoutHeight(density)
    val albumHeight = if (hasAlbum) secondaryTextHeight else 0.dp
    val artistHeight = if (hasArtist) secondaryTextHeight else 0.dp
    val collapsedTextHeight = collapsedTitleHeight + albumHeight + artistHeight

    val collapsedArtworkSize = collapsedContent.height * collapsedArtworkHeightFraction
    val collapsedItemVertSpacing = (
        collapsedContent.height -
            collapsedArtworkSize -
            collapsedTextHeight -
            collapsedControlSize -
            collapsedProgressHeight
        ) / 4

    val collapsedArtwork = Bounds(
        left = (collapsedContent.width - collapsedArtworkSize) / 2F,
        top = collapsedContent.top + collapsedItemVertSpacing,
        width = collapsedArtworkSize,
        height = collapsedArtworkSize
    )

    val collapsedProgress = Bounds(
        left = collapsedContent.left,
        top = collapsedContent.bottom - 16.dp,
        width = collapsedContent.width,
        height = collapsedProgressHeight
    )

    val collapsedTextLeft = collapsedContent.left
    val collapsedTextWidth = collapsedContent.width

    val collapsedTitle = Bounds(
        left = collapsedTextLeft,
        top = collapsedArtwork.bottom + collapsedItemVertSpacing,
        width = collapsedTextWidth,
        height = collapsedTitleHeight
    )
    val collapsedArtist = Bounds(
        left = collapsedTextLeft,
        top = collapsedTitle.bottom,
        width = collapsedTextWidth,
        height = artistHeight
    )
    val collapsedAlbum = Bounds(
        left = collapsedTextLeft,
        top = collapsedArtist.bottom,
        width = collapsedTextWidth,
        height = albumHeight
    )

    val collapsedControlsPadding = (collapsedContent.width - collapsedControlSize * 5) / 2
    val collapsedControlsTop = collapsedAlbum.bottom + collapsedItemVertSpacing

    val collapsedRepeat = Bounds(
        left = collapsedContent.left + collapsedControlsPadding,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedPrevTrack = Bounds(
        left = collapsedRepeat.right,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedPlayPause = Bounds(
        left = collapsedPrevTrack.right,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedNextTrack = Bounds(
        left = collapsedPlayPause.right,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )
    val collapsedShuffle = Bounds(
        left = collapsedNextTrack.right,
        top = collapsedControlsTop,
        width = collapsedControlSize,
        height = collapsedControlSize
    )

    // Expanded state
    val expandedBackground = Bounds(
        left = 0.dp,
        top = 0.dp,
        width = width,
        height = height
    )

    val expandedPlaylist = Bounds(
        left = width * (1F - expandedPlaylistWidthFraction),
        top = 0.dp,
        width = width * expandedPlaylistWidthFraction,
        height = height
    )

    val expandedContent = Bounds(
        left = startInset,
        top = topInset,
        width = width - expandedPlaylist.width - startInset,
        height = height - topInset - bottomInset
    )

    val expandedArtworkSize = expandedContent.height * expandedArtworkHeightFraction
    val expandedArtwork = Bounds(
        left = expandedContent.left + 16.dp,
        top = toolbarHeight + 16.dp,
        width = expandedArtworkSize,
        height = expandedArtworkSize
    )

    val heightAvailableForExpandedControls =
        height - expandedArtwork.bottom - bottomInset - expandedArtworkControlsPadding - 16.dp
    val expandedControlsVertSpacing =
        (heightAvailableForExpandedControls - expandedControlsSize - expandedProgressHeight) / 3

    val expandedProgress = Bounds(
        left = expandedContent.left + 8.dp,
        top = expandedArtwork.bottom + expandedArtworkControlsPadding + expandedControlsVertSpacing,
        width = expandedContent.width - 16.dp,
        height = expandedProgressHeight
    )

    val expandedControlsPadding = 16.dp
    val expandedControlsWidth = expandedContent.width - expandedControlsPadding * 2
    val expandedControlsTop = expandedProgress.bottom + expandedControlsVertSpacing
    val expandedControlsHorzSpacing = (expandedControlsWidth - expandedControlsSize * 5) / 4

    val expandedRepeat = Bounds(
        left = expandedContent.left + expandedControlsPadding,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedPrevTrack = Bounds(
        left = expandedRepeat.right + expandedControlsHorzSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedPlayPause = Bounds(
        left = expandedPrevTrack.right + expandedControlsHorzSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedNextTrack = Bounds(
        left = expandedPlayPause.right + expandedControlsHorzSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )
    val expandedShuffle = Bounds(
        left = expandedNextTrack.right + expandedControlsHorzSpacing,
        top = expandedControlsTop,
        width = expandedControlsSize,
        height = expandedControlsSize
    )

    val expandedTitleHeight = expandedTitleSize.estimateLayoutHeight(density)
    val expandedTextHeight = expandedTitleHeight + albumHeight + artistHeight
    val expandedTextSpacing = (expandedArtwork.height - expandedTextHeight) / 2
    val expandedTextLeft = expandedArtwork.right + 8.dp
    val expandedTextWidth = expandedContent.width - expandedTextLeft - 8.dp

    val expandedTitle = Bounds(
        left = expandedTextLeft,
        top = expandedArtwork.top + expandedTextSpacing,
        width = expandedTextWidth,
        height = expandedTitleHeight
    )
    val expandedArtist = Bounds(
        left = expandedTextLeft,
        top = expandedTitle.bottom,
        width = expandedTextWidth,
        height = artistHeight
    )
    val expandedAlbum = Bounds(
        left = expandedTextLeft,
        top = expandedArtist.bottom,
        width = expandedTextWidth,
        height = albumHeight
    )

    val contentCornerRadius = lerp(16.dp, 0.dp, progress)

    return PlayerGeometry(
        content = ContentLayoutSpec(
            bounds = lerp(collapsedBackground, expandedBackground, progress),
            corners = CornerSizes(
                topStart = contentCornerRadius,
                topEnd = 0.dp,
                bottomStart = contentCornerRadius,
                bottomEnd = 0.dp
            ),
            dragDirection = Orientation.Horizontal,
            dragDistance = density.run {
                (expandedBackground.width - collapsedBackground.width).toPx()
            }
        ),

        playlist = PlaylistLayoutSpec(
            bounds = lerp(collapsedPlaylist, expandedPlaylist, progress),
            peekHeight = Dp.Unspecified,
            padding = PaddingValues(
                top = topInset,
                end = endInset,
                bottom = bottomInset
            ),
            alpha = if (collapsedPlaylistHeightFraction > 0) {
                1F
            } else {
                fadeInWindow(progress, 0.5F, 1F)
            }
        ),

        progress = ProgressLayoutSpec(
            full = ControlLayoutSpec(
                bounds = expandedProgress,
                alpha = fadeInWindow(progress, 0.5F, 0.7F)
            ),
            mini = ControlLayoutSpec(
                bounds = collapsedProgress,
                alpha = fadeOutWindow(progress, 0.5F, 0.7F)
            )
        ),

        toolbar = ControlLayoutSpec(
            bounds = Bounds(
                left = 0.dp,
                top = 0.dp,
                width = width,
                height = 64.dp + topInset
            ),
            alpha = fadeInWindow(progress, .50f, .70f)
        ),

        artwork = lerp(collapsedArtwork, expandedArtwork, progress),

        title = TextLayoutSpec(
            bounds = lerp(collapsedTitle, expandedTitle, progress),
            fontSize = lerp(collapsedTitleSize, expandedTitleSize, progress)
        ),
        artist = TextLayoutSpec(
            bounds = lerp(collapsedArtist, expandedArtist, progress),
            fontSize = secondaryTextSize
        ),
        album = TextLayoutSpec(
            bounds = lerp(collapsedAlbum, expandedAlbum, progress),
            fontSize = secondaryTextSize
        ),

        textAlignmentProgress = 0.5F, // always center aligned

        controls = ControlsLayoutSpec(
            repeat = ControlLayoutSpec(
                bounds = lerp(collapsedRepeat, expandedRepeat, progress),
                alpha = 1F
            ),
            prevTrack = ControlLayoutSpec(
                bounds = lerp(collapsedPrevTrack, expandedPrevTrack, progress),
                alpha = 1F
            ),
            playPause = ControlLayoutSpec(
                bounds = lerp(collapsedPlayPause, expandedPlayPause, progress),
                alpha = 1F
            ),
            nextTrack = ControlLayoutSpec(
                bounds = lerp(collapsedNextTrack, expandedNextTrack, progress),
                alpha = 1F
            ),
            shuffle = ControlLayoutSpec(
                bounds = lerp(collapsedShuffle, expandedShuffle, progress),
                alpha = 1F
            )
        )
    )
}

private fun TextUnit.estimateLayoutHeight(density: Density) = with(density) {
    toDp() * 1.3F
}

private fun lerp(start: Bounds, end: Bounds, fraction: Float) = Bounds(
    lerp(start.left, end.left, fraction),
    top = lerp(start.top, end.top, fraction),
    width = lerp(start.width, end.width, fraction),
    height = lerp(start.height, end.height, fraction)
)

private fun fadeOutWindow(progress: Float, start: Float, end: Float) = when {
    progress <= start -> 1F
    progress >= end -> 0F
    else -> 1F - (progress - start) / (end - start)
}

private fun fadeInWindow(progress: Float, start: Float, end: Float) = when {
    progress <= start -> 0f
    progress >= end -> 1f
    else -> (progress - start) / (end - start)
}
