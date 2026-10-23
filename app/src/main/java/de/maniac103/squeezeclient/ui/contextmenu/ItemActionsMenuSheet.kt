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

package de.maniac103.squeezeclient.ui.contextmenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ArtworkImage

@Composable
fun ItemActionsMenuSheetContent(
    viewModel: ItemActionsViewModel,
    serverConfig: ServerConfiguration?,
    // FIXME: sealed class for selection listener param
    actionSelectedListener: (ItemActionsViewModel.ActionItem) -> Unit
) {
    val item = viewModel.item
    val actions by viewModel.actionsFlow.collectAsStateWithLifecycle(emptyList())
    val busyAction by viewModel.busyActionFlow.collectAsStateWithLifecycle(null)

    LazyColumn {
        stickyHeader {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                if (viewModel.item.isValidIcon) {
                    ArtworkImage(
                        artwork = item,
                        serverConfig = serverConfig,
                        placeholderDrawableResId = null,
                        modifier = Modifier
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                            .size(40.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (!item.subText.isNullOrEmpty()) {
                        Text(
                            text = item.subText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
        items(actions) { action ->
            ContextMenuItemRow(
                title = stringResource(action.labelResId),
                busy = action == busyAction,
                selectable = true,
                modifier = Modifier.clickable { actionSelectedListener(action) }
            )
        }
    }
}
