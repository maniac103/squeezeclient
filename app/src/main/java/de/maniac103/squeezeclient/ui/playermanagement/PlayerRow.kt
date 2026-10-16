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

package de.maniac103.squeezeclient.ui.playermanagement

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.model.Playlist
import de.maniac103.squeezeclient.ui.composables.TextResource
import kotlin.math.roundToInt

internal enum class RowState {
    Idle,
    Dragged,
    DropTarget
}

internal enum class RowPosition {
    Single,
    First,
    Middle,
    Last
}

@Composable
internal fun DragDropAwareRow(
    state: RowState,
    position: RowPosition,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    val animationSpec = tween<Float>(250)
    val rowScale by animateFloatAsState(
        targetValue = if (state == RowState.Dragged) 0.95F else 1F,
        animationSpec = animationSpec,
        label = "rowScale"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (state == RowState.DropTarget) 0.9F else 1F,
        animationSpec = animationSpec,
        label = "contentScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (state == RowState.Dragged) 0.9F else 1F,
        animationSpec = animationSpec,
        label = "contentAlpha"
    )

    val background = when (state) {
        RowState.Idle -> MaterialTheme.colorScheme.surfaceContainerHigh
        RowState.Dragged -> MaterialTheme.colorScheme.surfaceContainerHighest
        RowState.DropTarget -> MaterialTheme.colorScheme.secondaryContainer
    }

    val isFirst = position == RowPosition.Single || position == RowPosition.First
    val isLast = position == RowPosition.Single || position == RowPosition.Last

    val radiusAnimationSpec = tween<Dp>(250)
    val topCornerRadius by animateDpAsState(
        targetValue = if (isFirst) 12.dp else 4.dp,
        animationSpec = radiusAnimationSpec,
        label = "topCornerRadius"
    )
    val bottomCornerRadius by animateDpAsState(
        targetValue = if (isLast) 12.dp else 4.dp,
        animationSpec = radiusAnimationSpec,
        label = "topCornerRadius"
    )

    Card(
        shape = RoundedCornerShape(
            topStart = topCornerRadius,
            topEnd = topCornerRadius,
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius
        ),
        colors = CardDefaults.cardColors(
            containerColor = background
        ),
        modifier = modifier
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (isFirst) 4.dp else 1.dp,
                bottom = if (isLast) 4.dp else 1.dp
            )
            .graphicsLayer(
                scaleX = rowScale,
                scaleY = rowScale,
                alpha = contentAlpha
            )
    ) {
        Row(
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .graphicsLayer(
                    scaleX = contentScale,
                    scaleY = contentScale
                )
        ) {
            content()
        }
    }
}

@Composable
internal fun PlayerRow(
    player: PlayerData,
    state: RowState,
    position: RowPosition,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onChangePlaybackState: (PlayerId, PlayerStatus.PlayState) -> Unit = { _, _ -> },
    onTogglePower: (PlayerId) -> Unit = {},
    onChangeVolume: (PlayerId, Int) -> Unit = { _, _ -> }
) {
    val isMaster = player.master == null

    DragDropAwareRow(
        state = state,
        position = position,
        modifier = modifier
    ) {
        if (!isMaster) {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier.weight(1F)
        ) {
            Text(
                text = player.name,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = player.modelName.resolve(),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (isMaster && player.nowPlayingInfo != null) {
                Text(
                    text = "${player.nowPlayingInfo.title} · ${player.nowPlayingInfo.artist}",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMaster) {
                    val isPlaying = player.playbackState == PlayerStatus.PlayState.Playing
                    IconButton(
                        onClick = {
                            val newState = when {
                                isPlaying -> PlayerStatus.PlayState.Paused
                                else -> PlayerStatus.PlayState.Playing
                            }
                            onChangePlaybackState(player.playerId, newState)
                        }
                    ) {
                        val iconResId = when {
                            isPlaying -> R.drawable.ic_pause_24dp
                            else -> R.drawable.ic_play_24dp
                        }
                        Icon(
                            imageVector = ImageVector.vectorResource(iconResId),
                            contentDescription = null // FIXME
                        )
                    }
                }
                if (player.canPowerOff) {
                    IconButton(
                        onClick = { onTogglePower(player.playerId) }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_power_24dp),
                            contentDescription = null, // FIXME
                            tint = if (player.powered) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (player.volume != null) {
                    val volumeSliderState = rememberSliderState(
                        value = player.volume.toFloat(),
                        trackRange = 0F..100F
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    Slider(
                        state = volumeSliderState,
                        interactionSource = interactionSource,
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(4.dp)
                            )
                        },
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = interactionSource,
                                thumbSize = DpSize(4.dp, 16.dp)
                            )
                        },
                        onValueChange = { newValue ->
                            volumeSliderState.value = newValue
                        },
                        onValueChangeFinished = {
                            onChangeVolume(player.playerId, volumeSliderState.value.roundToInt())
                        }
                    )
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = dragHandleModifier
                .size(48.dp)
                .align(Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_drag_horizontal_24dp),
                contentDescription = null // FIXME
            )
        }
    }
}

@Composable
internal fun DragTargetRow(
    state: RowState,
    modifier: Modifier = Modifier
) = DragDropAwareRow(
    state = state,
    position = RowPosition.Single,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier.fillMaxWidth()
) {
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.ic_link_off_24dp),
        contentDescription = null, // FIXME
        modifier = Modifier
            .size(96.dp)
    )
}

@Preview
@Composable
fun PlayerRowPreview() {
    val masterData = PlayerData(
        playerId = PlayerId("11:22:33:44:55:66"),
        name = "Master player",
        modelName = TextResource.Raw("Player model"),
        canPowerOff = true,
        nowPlayingInfo = Playlist.PlaylistItem(
            title = "Song title",
            artist = "Artist",
            album = "Album",
            actions = JiveActions.EMPTY
        ),
        playbackState = PlayerStatus.PlayState.Playing,
        volume = 70,
        powered = true,
        master = null
    )
    val slaveData = PlayerData(
        playerId = PlayerId("00::11:22:33:44:55"),
        name = "Slave player",
        modelName = TextResource.Raw("Player model"),
        canPowerOff = true,
        nowPlayingInfo = null,
        playbackState = PlayerStatus.PlayState.Paused,
        volume = 70,
        powered = false,
        master = PlayerId("11:22:33:44:55:66")
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
        ) {
            PlayerRow(masterData, RowState.Idle, RowPosition.First)
            PlayerRow(slaveData, RowState.Idle, RowPosition.Last)
            DragTargetRow(RowState.Idle)
        }
    }
}