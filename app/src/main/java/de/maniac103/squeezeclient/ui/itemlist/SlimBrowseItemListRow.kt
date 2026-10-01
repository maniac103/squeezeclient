package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.Theme

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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        if (artwork != null) {
            ArtworkImage(
                artwork = artwork,
                serverConfig = serverConfig,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(40.dp)
                    .align(Alignment.CenterVertically)
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Theme.TextStyles.listItemPrimary
            )

            if (!subtext.isNullOrEmpty()) {
                Text(
                    text = subtext,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = Theme.TextStyles.listItemSecondary
                )
            }
        }

        val extraModifier = Modifier
            .align(Alignment.CenterVertically)
            .padding(end = 16.dp)

        when {
            busy -> {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = extraModifier.size(24.dp)
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.Choice -> {
                Text(
                    text = trailingWidget.label,
                    style = Theme.TextStyles.listItemSecondary,
                    modifier = extraModifier
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.Checkbox -> {
                Checkbox(
                    checked = trailingWidget.checked,
                    onCheckedChange = { },
                    modifier = extraModifier
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.Radio -> {
                RadioButton(
                    selected = trailingWidget.checked,
                    onClick = { },
                    modifier = extraModifier
                )
            }

            trailingWidget is SlimBrowseItemTrailingWidget.ContextMenu -> {
                Image(
                    ImageVector.vectorResource(R.drawable.ic_menu_overflow),
                    contentScale = ContentScale.Inside,
                    contentDescription = null, // FIXME
                    modifier = extraModifier
                        .size(48.dp)
                        .clickable(
                            onClick = trailingWidget.clickListener,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        )
                )
            }
        }
    }
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
        SlimBrowseItemListRow("Menu", "Subtext", SlimBrowseItemTrailingWidget.ContextMenu {}, dummyArtwork, null, false)
        SlimBrowseItemListRow("Choice", "Subtext", SlimBrowseItemTrailingWidget.Choice("On"), dummyArtwork, null, false)
        SlimBrowseItemListRow("Radio", "Subtext", SlimBrowseItemTrailingWidget.Radio(true), dummyArtwork, null, false)
        SlimBrowseItemListRow("Checkbox", "Subtext", SlimBrowseItemTrailingWidget.Checkbox(true), dummyArtwork, null, false)
    }
}
