package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.ArtworkImage

@Composable
fun SlimBrowseItemListRow(
    item: SlimBrowseItemList.SlimBrowseItem,
    serverConfig: ServerConfiguration?,
    showIcon: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) = SlimBrowseItemListRow(
    title = item.title,
    subtext = item.subText ?: item.extraInfo,
    trailingWidget = item.extractTrailingWidget(contextMenuClickListener),
    artwork = if (showIcon) item else null,
    serverConfig = serverConfig,
    busy = busy,
    modifier = modifier
)

@Composable
fun SlimBrowseItemListRow(
    title: String,
    subtext: String?,
    trailingWidget: SlimBrowseItemTrailingWidget?,
    artwork: ArtworkItem?,
    serverConfig: ServerConfiguration?,
    busy: Boolean,
    modifier: Modifier = Modifier
) = ListItem(
    leadingContent = {
        if (artwork != null) {
            ArtworkImage(
                artwork = artwork,
                serverConfig = serverConfig,
                modifier = Modifier
                    .size(40.dp)
            )
        }
    },
    trailingContent = {
        when {
            busy -> {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.Choice -> {
                Text(
                    text = trailingWidget.label,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.Checkbox -> {
                Checkbox(
                    checked = trailingWidget.checked,
                    onCheckedChange = { }
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.Radio -> {
                RadioButton(
                    selected = trailingWidget.checked,
                    onClick = { }
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.ContextMenu -> {
                IconButton(
                    onClick = trailingWidget.clickListener
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_menu_overflow),
                        contentDescription = null // FIXME
                    )
                }
            }
        }
    },
    supportingContent = {
        if (!subtext.isNullOrEmpty()) {
            Text(
                text = subtext,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    modifier = modifier
) {
    Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Preview
@Composable
fun SlimBrowseItemListRowPreview() {
    val dummyArtwork = object : ArtworkItem {
        override val icon = null
        override val iconId = null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SlimBrowseItemListRow("Title", "Subtext", null, dummyArtwork, null, false)
        SlimBrowseItemListRow("No Subtext", null, null, dummyArtwork, null, false)
        SlimBrowseItemListRow("No icon", null, null, null, null, false)
        SlimBrowseItemListRow("Busy", "Subtext", null, dummyArtwork, null, true)
        SlimBrowseItemListRow(
            "Menu",
            "Subtext",
            SlimBrowseItemTrailingWidget.ContextMenu {},
            dummyArtwork,
            null,
            false
        )
        SlimBrowseItemListRow(
            "Choice",
            "Subtext",
            SlimBrowseItemTrailingWidget.Choice("On"),
            dummyArtwork,
            null,
            false
        )
        SlimBrowseItemListRow(
            "Radio",
            "Subtext",
            SlimBrowseItemTrailingWidget.Radio(true),
            dummyArtwork,
            null,
            false
        )
        SlimBrowseItemListRow(
            "Checkbox",
            "Subtext",
            SlimBrowseItemTrailingWidget.Checkbox(true),
            dummyArtwork,
            null,
            false
        )
    }
}
