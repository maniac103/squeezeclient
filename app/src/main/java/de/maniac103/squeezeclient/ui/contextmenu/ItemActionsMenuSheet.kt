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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.model.ServerConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    viewModel: ItemActionsViewModel = viewModel(),
    serverConfig: ServerConfiguration?,
    actionSelectedListener: (ItemActionsViewModel.ActionItem) -> Unit // FIXME: sealed class
) {
    val item = viewModel.item
    val actions by viewModel.actionsFlow.collectAsState(emptyList())
    val busyAction by viewModel.busyActionFlow.collectAsState(null)

    LazyColumn {
        stickyHeader {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                viewModel.item.extractIconUrl()?.let {
                    ArtworkImage(
                        artwork = item,
                        serverConfig = serverConfig,
                        usePlaceholder = false,
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
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
        items(actions) { action ->
            ContextMenuListRow(
                title = stringResource(action.labelResId),
                busy = action == busyAction,
                selectable = true,
                modifier = Modifier.clickable { actionSelectedListener(action) }
            )
        }
    }
}