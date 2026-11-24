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

package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.ui.composables.DelayedAnimatedVisibility
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton
import de.maniac103.squeezeclient.ui.composables.SmallCircularWavyProgressIndicator

@Composable
fun JiveUiItemRow(
    item: JiveUiItem,
    serverConfig: ServerConfiguration?,
    index: Int,
    count: Int,
    showIcon: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onContextMenuSelected: () -> Unit = {}
) = SegmentedListItem(
    onClick = onClick,
    shapes = ListItemDefaults.segmentedShapes(index, count),
    leadingContent = item.icon
        ?.takeIf { showIcon }
        ?.let { icon ->
            {
                JiveUiItemRowIcon(
                    icon = icon,
                    serverConfig = serverConfig,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
    trailingContent = {
        DelayedAnimatedVisibility(show = busy) {
            SmallCircularWavyProgressIndicator()
        }
        if (!busy && item.trailingWidget != null) {
            JiveUiItemRowTrailingWidget(
                widget = item.trailingWidget,
                onContextMenuSelected = onContextMenuSelected
            )
        }
    },
    supportingContent = {
        if (!item.subText.isNullOrEmpty()) {
            Text(
                text = item.subText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    modifier = modifier
) {
    Text(
        text = item.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun JiveUiItemRowIcon(
    icon: JiveUiItemIcon,
    serverConfig: ServerConfiguration?,
    modifier: Modifier
) = when (icon) {
    is JiveUiItemIcon.Spacer -> {
        Spacer(modifier)
    }

    is JiveUiItemIcon.Icon -> {
        Icon(
            imageVector = ImageVector.vectorResource(icon.iconResId),
            contentDescription = null,
            modifier = modifier
        )
    }

    is JiveUiItemIcon.Artwork -> {
        ArtworkImage(
            artwork = icon.artwork,
            serverConfig = serverConfig,
            modifier = modifier
        )
    }
}

@Composable
internal fun JiveUiItemRowTrailingWidget(
    widget: JiveUiItemTrailingWidget,
    onContextMenuSelected: () -> Unit,
    modifier: Modifier = Modifier,
    contextMenuModifier: Modifier = modifier
) = when (widget) {
    is JiveUiItemTrailingWidget.Choice -> {
        Text(
            text = widget.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier
        )
    }

    is JiveUiItemTrailingWidget.Checkbox -> {
        Checkbox(
            checked = widget.checked,
            onCheckedChange = null,
            modifier = modifier
        )
    }

    is JiveUiItemTrailingWidget.Radio -> {
        RadioButton(
            selected = widget.checked,
            onClick = null,
            modifier = modifier
        )
    }

    is JiveUiItemTrailingWidget.ContextMenu -> {
        ResourceIconButton(
            drawableResId = R.drawable.ic_menu_overflow,
            contentDescriptionResId = R.string.content_description_context_menu,
            onClick = onContextMenuSelected,
            modifier = contextMenuModifier
        )
    }
}

internal fun generateJiveUiPreviewWidgets(): List<Pair<JiveUiItem, Boolean>> {
    val dummyArtwork = object : ArtworkItem {
        override val icon = null
        override val iconId = null
    }
    val dummyIcon = JiveUiItemIcon.Artwork(dummyArtwork)

    return listOf(
        JiveUiItem("Title", "Subtext", dummyIcon, null) to false,
        JiveUiItem("No Subtext", null, dummyIcon, null) to false,
        JiveUiItem("Spacer", null, JiveUiItemIcon.Spacer, null) to false,
        JiveUiItem("No icon", null, null, null) to false,
        JiveUiItem("Choice", "Subtext", dummyIcon, JiveUiItemTrailingWidget.Choice("On")) to false,
        JiveUiItem("Radio", "Subtext", dummyIcon, JiveUiItemTrailingWidget.Radio(true)) to false,
        JiveUiItem("Checkbox", null, dummyIcon, JiveUiItemTrailingWidget.Checkbox(true)) to false,
        JiveUiItem("Busy", "Subtext", dummyIcon, null) to true
    )
}

@Preview
@Composable
fun JiveUiItemRowPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        val widgets = generateJiveUiPreviewWidgets()
        widgets.forEachIndexed { index, (item, busy) ->
            JiveUiItemRow(
                item = item,
                index = index,
                count = widgets.size,
                busy = busy,
                serverConfig = null,
                showIcon = true
            )
        }
    }
}
