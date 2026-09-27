package de.maniac103.squeezeclient.ui.itemlist

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.placeholder
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.Theme

sealed class SlimBrowseItemListExtra {
    class Choice(val label: String) : SlimBrowseItemListExtra()
    class Checkbox(val checked: Boolean) : SlimBrowseItemListExtra()
    class Radio(val checked: Boolean) : SlimBrowseItemListExtra()
    class ContextMenu(val clickListener: () -> Unit) : SlimBrowseItemListExtra()
}

sealed class SlimBrowseIcon {
    object None : SlimBrowseIcon()
    class Icon(val request : ImageRequest) : SlimBrowseIcon()
}

@Composable
fun SlimBrowseItemListEntry(
    item: SlimBrowseItemList.SlimBrowseItem,
    serverConfig: ServerConfiguration?,
    showIcon: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val icon = if (showIcon) {
        SlimBrowseIcon.Icon(item.buildImageRequest(LocalContext.current, serverConfig))
    } else {
        SlimBrowseIcon.None
    }
    val extra = item.extractExtra(contextMenuClickListener)

    return SlimBrowseItemListEntry(
        title = item.title,
        subtext = item.subText ?: item.extraInfo,
        extra = extra,
        icon = icon,
        busy = busy,
        modifier = modifier
    )
}

@Composable
fun SlimBrowseItemGridEntry(
    item: SlimBrowseItemList.SlimBrowseItem,
    serverConfig: ServerConfiguration?,
    busy: Boolean,
    modifier: Modifier = Modifier,
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) {
    val icon = SlimBrowseIcon.Icon(item.buildImageRequest(LocalContext.current, serverConfig))
    val extra = item.extractExtra(contextMenuClickListener)

    // FIXME
    return SlimBrowseItemListEntry(
        title = item.title,
        subtext = item.subText ?: item.extraInfo,
        extra = extra,
        icon = icon,
        busy = busy,
        modifier = modifier
    )
}

@Composable
fun SlimBrowseItemListEntry(
    title: String,
    subtext: String?,
    extra: SlimBrowseItemListExtra?,
    icon: SlimBrowseIcon,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        val iconModifier = Modifier
            .padding(horizontal = 16.dp)
            .size(40.dp)
            .align(Alignment.CenterVertically)
        when (icon) {
            is SlimBrowseIcon.None -> {
                Spacer(modifier = Modifier.size(16.dp))
            }

            is SlimBrowseIcon.Icon -> {
                AsyncImage(
                    icon.request,
                    contentDescription = null,
                    modifier = iconModifier
                        .clip(RoundedCornerShape(8.dp))
                )
            }
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

            extra is SlimBrowseItemListExtra.Choice -> {
                Text(
                    text = extra.label,
                    style = Theme.TextStyles.listItemSecondary,
                    modifier = extraModifier
                )
            }

            extra is SlimBrowseItemListExtra.Checkbox -> {
                Checkbox(
                    checked = extra.checked,
                    onCheckedChange = { },
                    modifier = extraModifier
                )
            }

            extra is SlimBrowseItemListExtra.Radio -> {
                RadioButton(
                    selected = extra.checked,
                    onClick = { },
                    modifier = extraModifier
                )
            }

            extra is SlimBrowseItemListExtra.ContextMenu -> {
                Image(
                    painterResource(R.drawable.ic_menu_overflow),
                    contentScale = ContentScale.Inside,
                    contentDescription = null, // FIXME
                    modifier = extraModifier
                        .size(48.dp)
                        .clickable(
                            onClick = extra.clickListener,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false)
                        )
                )
            }
        }
    }
}

@Composable
fun SlimBrowseItemGridEntry(
    title: String,
    subtext: String?,
    extra: SlimBrowseItemListExtra?,
    iconRequest: ImageRequest,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        AsyncImage(
            iconRequest,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1F)
                .clip(RoundedCornerShape(8.dp))
        )

        Column {
            Row {
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
                //.align(Alignment.CenterVertically)
                .padding(end = 16.dp)

            when {
                busy -> {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = extraModifier.size(24.dp)
                    )
                }

                extra is SlimBrowseItemListExtra.Choice -> {
                    Text(
                        text = extra.label,
                        style = Theme.TextStyles.listItemSecondary,
                        modifier = extraModifier
                    )
                }

                extra is SlimBrowseItemListExtra.Checkbox -> {
                    Checkbox(
                        checked = extra.checked,
                        onCheckedChange = { },
                        modifier = extraModifier
                    )
                }

                extra is SlimBrowseItemListExtra.Radio -> {
                    RadioButton(
                        selected = extra.checked,
                        onClick = { },
                        modifier = extraModifier
                    )
                }

                extra is SlimBrowseItemListExtra.ContextMenu -> {
                    Image(
                        painterResource(R.drawable.ic_menu_overflow),
                        contentScale = ContentScale.Inside,
                        contentDescription = null, // FIXME
                        modifier = extraModifier
                            .size(48.dp)
                            .clickable(
                                onClick = extra.clickListener,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false)
                            )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SlimBrowseItemListEntryPreview() {
    val dummyRequest = ImageRequest.Builder(LocalContext.current)
        .data(null)
        .placeholder(R.drawable.ic_disc_24dp)
        .build()

    Column(modifier = Modifier.fillMaxWidth()) {
        SlimBrowseItemListEntry("Title", "Subtext", null, SlimBrowseIcon.Icon(dummyRequest), false)
        SlimBrowseItemListEntry("No Subtext", null, null, SlimBrowseIcon.Icon(dummyRequest), false)
        SlimBrowseItemListEntry("No icon", null, null, SlimBrowseIcon.None, false)
        SlimBrowseItemListEntry("Busy", "Subtext", null, SlimBrowseIcon.Icon(dummyRequest), true)
        SlimBrowseItemListEntry("Menu", "Subtext", SlimBrowseItemListExtra.ContextMenu {}, SlimBrowseIcon.Icon(dummyRequest), false)
        SlimBrowseItemListEntry("Choice", "Subtext", SlimBrowseItemListExtra.Choice("On"), SlimBrowseIcon.Icon(dummyRequest), false)
        SlimBrowseItemListEntry("Radio", "Subtext", SlimBrowseItemListExtra.Radio(true), SlimBrowseIcon.Icon(dummyRequest), false)
        SlimBrowseItemListEntry("Checkbox", "Subtext", SlimBrowseItemListExtra.Checkbox(true), SlimBrowseIcon.Icon(dummyRequest), false)
    }
}

fun SlimBrowseItemList.SlimBrowseItem.extractExtra(
    contextMenuClickListener: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {}
) = actions?.let { actions ->
    when {
        actions.checkbox != null ->
            SlimBrowseItemListExtra.Checkbox(actions.checkbox.state)

        actions.radio != null ->
            SlimBrowseItemListExtra.Radio(actions.radio.state)

        actions.choices != null ->
            SlimBrowseItemListExtra.Choice(
                actions.choices.items[actions.choices.selectedIndex].title,
            )

        actions.hasContextMenu ->
            SlimBrowseItemListExtra.ContextMenu {
                contextMenuClickListener(this)
            }

        else -> null
    }
}

fun ArtworkItem?.buildImageRequest(context: Context, serverConfig: ServerConfiguration?): ImageRequest {
    val iconUrl = this?.extractIconUrl(serverConfig)
    val requestBuilder = ImageRequest.Builder(context)
        .data(iconUrl)
        .placeholder(R.drawable.ic_disc_24dp)

    serverConfig?.credentialsAsAuthorizationHeader?.let {
        val headers = NetworkHeaders.Builder()
            .set("Authorization", it)
            .build()
        requestBuilder.httpHeaders(headers)
    }

    return requestBuilder.build()
}
