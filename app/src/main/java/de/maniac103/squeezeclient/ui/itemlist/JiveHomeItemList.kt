package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.ui.Theme
import de.maniac103.squeezeclient.ui.composables.LazyColumnScrollStateHelper

@Composable
fun JiveHomeItemListEntry(
    title: String,
    subtext: String?,
    choiceLabel: String?,
    iconResourceId: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 8.dp)
    ) {
        val iconModifier = Modifier
            .padding(horizontal = 16.dp)
            .size(40.dp)
            .align(Alignment.CenterVertically)
        if (iconResourceId != null) {
            Image(
                ImageVector.vectorResource(iconResourceId),
                contentDescription = null,
                modifier = iconModifier
            )
        } else {
            Spacer(modifier = iconModifier)
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

        if (!choiceLabel.isNullOrEmpty()) {
            Text(
                text = choiceLabel,
                style = Theme.TextStyles.listItemSecondary,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 16.dp)
            )
        }
    }
}


@Composable
fun JiveHomeItemList(
    viewModel: JiveHomeItemListViewModel = viewModel(),
    itemSelectionListener: (JiveHomeMenuItem) -> Unit = {}
) {
    val menuEntries by viewModel.homeMenuItemsFlow.collectAsState(emptyList())

    LazyColumnScrollStateHelper { state ->
        LazyColumn(
            state = state,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(menuEntries) { entry ->
                JiveHomeItemListEntry(
                    title = entry.title,
                    subtext = entry.subText,
                    choiceLabel = entry.choiceLabel,
                    iconResourceId = entry.iconResourceId,
                    modifier = Modifier.clickable { itemSelectionListener(entry.source) }
                )
            }
        }
    }
}

@Preview
@Composable
fun JiveHomeItemListEntryPreview() {
    JiveHomeItemListEntry("Some item", "Some subtext", "Yes", R.drawable.hm_albumartists)
}