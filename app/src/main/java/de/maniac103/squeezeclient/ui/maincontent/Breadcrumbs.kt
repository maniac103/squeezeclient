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

package de.maniac103.squeezeclient.ui.maincontent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import de.maniac103.squeezeclient.ui.composables.ResourceIcon
import de.maniac103.squeezeclient.ui.composables.toTextResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun Breadcrumbs(
    stateFlow: StateFlow<BreadcrumbsState>,
    serverConfig: ServerConfiguration?,
    modifier: Modifier = Modifier,
    onHomeClicked: () -> Unit = {},
    onGoBack: (levels: Int) -> Unit = {}
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val items = state.items

    if (items.isEmpty() && state.backProgress == null) {
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        ResourceIcon(
            drawableResId = R.drawable.ic_home_24dp,
            contentDescriptionResId = R.string.content_description_breadcrumbs_home,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onHomeClicked)
                .padding(8.dp)
        )

        val listState = rememberLazyListState()

        LaunchedEffect(items.size) {
            listState.animateScrollToItem(items.lastIndex)
        }

        LazyRow(
            state = listState,
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(items) { index, item ->
                val alphaModifier = Modifier.graphicsLayer {
                    alpha = state.backProgress
                        ?.takeIf { index == items.size - 1 }
                        ?.let { 1F - it }
                        ?: 1F
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BreadcrumbsTextSegment("›", alphaModifier)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(
                                onClick = { onGoBack(items.size - index - 1) }
                            )
                            .padding(8.dp)
                    ) {
                        item.icon?.let {
                            ArtworkImage(
                                it,
                                serverConfig,
                                modifier = alphaModifier
                                    .padding(end = 4.dp)
                                    .size(16.dp)
                            )
                        }

                        item.parentTitle?.let { parentTitle ->
                            BreadcrumbsTextSegment(parentTitle.resolve(), alphaModifier)
                            BreadcrumbsTextSegment(
                                text = "›",
                                modifier = alphaModifier.padding(horizontal = 8.dp)
                            )
                        }

                        BreadcrumbsTextSegment(item.title.resolve(), alphaModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbsTextSegment(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    maxLines = 1,
    modifier = modifier
)

@Preview
@Composable
fun BreadcrumbsPreview() {
    val items = listOf(
        BreadcrumbsItem("My music".toTextResource(), null, null),
        BreadcrumbsItem("Album artists".toTextResource(), null, null),
        BreadcrumbsItem(
            "Some context action".toTextResource(),
            "Some artist".toTextResource(),
            null
        )
    )
    val stateFlow = MutableStateFlow(BreadcrumbsState(items, 0.5F))
    Breadcrumbs(stateFlow, null)
}
