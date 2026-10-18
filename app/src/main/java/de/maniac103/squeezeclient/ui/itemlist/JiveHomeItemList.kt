package de.maniac103.squeezeclient.ui.itemlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.JiveHomeMenuItem

@Composable
fun JiveHomeItemListEntry(
    title: String,
    subtext: String?,
    choiceLabel: String?,
    iconResourceId: Int?,
    modifier: Modifier = Modifier
) = ListItem(
    leadingContent = {
        val iconModifier = Modifier
            .size(40.dp)

        if (iconResourceId != null) {
            Image(
                ImageVector.vectorResource(iconResourceId),
                contentDescription = null,
                modifier = iconModifier
            )
        } else {
            Spacer(modifier = iconModifier)
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
    trailingContent = {
        if (!choiceLabel.isNullOrEmpty()) {
            Text(
                text = choiceLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(end = 16.dp)
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

@Composable
fun JiveHomeItemList(
    viewModel: JiveHomeItemListViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    itemSelectionListener: (JiveHomeMenuItem) -> Unit = {}
) {
    val menuEntries by viewModel.homeMenuItemsFlow.collectAsState(emptyList())

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
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

@Preview
@Composable
fun JiveHomeItemListEntryPreview() {
    JiveHomeItemListEntry("Some item", "Some subtext", "Yes", R.drawable.hm_albumartists)
}
