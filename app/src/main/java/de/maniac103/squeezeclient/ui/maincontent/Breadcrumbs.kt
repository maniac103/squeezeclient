package de.maniac103.squeezeclient.ui.maincontent

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.model.ServerConfiguration
import de.maniac103.squeezeclient.ui.composables.ArtworkImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun Breadcrumbs(
    stateFlow: StateFlow<BreadcrumbsState>,
    serverConfig: ServerConfiguration?,
    onHomeClicked: () -> Unit = {},
    onGoBack: (levels: Int) -> Unit = {}
) {
    val state by stateFlow.collectAsState()
    val items = state.items

    if (items.isEmpty() && state.backProgress == null) {
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_24dp),
            contentDescription = null, // FIXME
            modifier = Modifier.clickable(onClick = onHomeClicked)
        )

        val listState = rememberLazyListState()

        LaunchedEffect(items.size) {
            listState.animateScrollToItem(items.lastIndex)
        }

        LazyRow(
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(items) { index, item ->
                val alphaModifier = state.backProgress
                    ?.takeIf { index == items.size - 1 }
                    ?.let { Modifier.alpha(1F - it) }
                    ?: Modifier

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = { onGoBack(items.size - index - 1) })
                ) {
                    item.parentTitle?.let { parentTitle ->
                        BreadcrumbsTextSegment(" › ", alphaModifier)
                        BreadcrumbsTextSegment(parentTitle, alphaModifier)
                    }

                    BreadcrumbsTextSegment(" › ", alphaModifier)

                    item.icon?.let {
                        ArtworkImage(
                            it,
                            serverConfig,
                            modifier = alphaModifier
                                .padding(end = 8.dp)
                                .size(16.dp)
                        )
                    }

                    BreadcrumbsTextSegment(item.title, alphaModifier)
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbsTextSegment(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    maxLines = 1,
    modifier = modifier
)

@Preview
@Composable
fun BreadcrumbsPreview() {
    val items = listOf(
        BreadcrumbsItem("My music", null, null),
        BreadcrumbsItem("Album artists", null, null),
        BreadcrumbsItem("Some context action", "Some artist", null)
    )
    val stateFlow = MutableStateFlow(BreadcrumbsState(items, 0.5F))
    Breadcrumbs(stateFlow, null)
}