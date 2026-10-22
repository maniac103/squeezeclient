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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val menuEntries by viewModel.homeMenuItemsFlow.collectAsStateWithLifecycle(emptyList())

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
