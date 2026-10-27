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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.ui.composables.ResourceIconButton
import kotlinx.coroutines.Job

@Composable
fun ContextMenuBottomSheetContent(
    viewModel: ContextMenuViewModel,
    serverConfig: ServerConfiguration?,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Job? = { null }
) {
    val pageStack by viewModel.pageStackFlow.collectAsStateWithLifecycle()
    val busyItem by viewModel.busyItemFlow.collectAsStateWithLifecycle()

    ContextMenuBottomSheetContent(
        pageStack = pageStack,
        busyItem = busyItem,
        parentItem = viewModel.parentItem,
        serverConfig = serverConfig,
        onNavigateBack = viewModel::popPage,
        onItemSelected = { item ->
            val goAction = item.actions?.goAction
            val job = if (goAction?.isContextMenu == true) {
                viewModel.pushPage(item, goAction)
                null
            } else {
                onItemSelected(item)
            }
            job?.let { viewModel.setItemBusy(item, it) }
        }
    )
}

@Composable
fun ContextMenuBottomSheetContent(
    pageStack: List<ContextMenuViewModel.Page>,
    parentItem: SlimBrowseItemList.SlimBrowseItem,
    busyItem: SlimBrowseItemList.SlimBrowseItem?,
    serverConfig: ServerConfiguration?,
    onItemSelected: (SlimBrowseItemList.SlimBrowseItem) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        ContextMenuHeader(
            item = parentItem,
            serverConfig = serverConfig
        )

        val currentPage = pageStack.last()
        AnimatedVisibility(visible = currentPage.canGoBack) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResourceIconButton(
                    drawableResId = R.drawable.ic_chevron_left_24dp,
                    contentDescriptionResId = R.string.content_description_context_menu_back,
                    onClick = onNavigateBack
                )
                Text(text = currentPage.parent.title)
            }
        }

        NavDisplay(
            backStack = pageStack,
            onBack = onNavigateBack,
            transitionSpec = {
                contextMenuPageEnter(1) togetherWith contextMenuPageExit(1)
            },
            popTransitionSpec = {
                contextMenuPageEnter(-1) togetherWith contextMenuPageExit(-1)
            },
            predictivePopTransitionSpec = {
                contextMenuPageEnter(-1) togetherWith contextMenuPageExit(-1)
            },
            entryProvider = entryProvider {
                entry<ContextMenuViewModel.Page> { page ->
                    LazyColumn {
                        items(page.items) { item ->
                            ContextMenuItemRow(
                                title = item.title,
                                busy = item == busyItem,
                                selectable = item.actions?.goAction != null,
                                modifier = Modifier
                                    .clickable(onClick = { onItemSelected(item) })
                            )
                        }
                    }
                }
            }
        )
    }
}

private fun contextMenuPageEnter(direction: Int) = slideInHorizontally(
    initialOffsetX = { it * direction },
    animationSpec = tween(
        durationMillis = 300,
        easing = EaseInOut
    )
)

private fun contextMenuPageExit(direction: Int) = slideOutHorizontally(
    targetOffsetX = { -it * direction },
    animationSpec = tween(
        durationMillis = 300,
        easing = EaseInOut
    )
)

@Composable
private fun ContextMenuHeader(
    item: SlimBrowseItemList.SlimBrowseItem,
    serverConfig: ServerConfiguration?
) = Row(
    modifier = Modifier.padding(horizontal = 16.dp)
) {
    if (item.isValidIcon) {
        ArtworkImage(
            artwork = item,
            serverConfig = serverConfig,
            placeholderDrawableResId = null,
            modifier = Modifier
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                .size(40.dp)
                .align(Alignment.CenterVertically)
        )
    }
    Column(
        modifier = Modifier.align(Alignment.CenterVertically)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
        item.subText?.let { subText ->
            Text(
                text = subText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}
