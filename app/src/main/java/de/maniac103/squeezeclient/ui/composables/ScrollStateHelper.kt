package de.maniac103.squeezeclient.ui.composables

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun LazyColumnScrollStateHelper(
    content: @Composable (LazyListState) -> Unit
) {
    val reporter = LocalAppBarScrollReporter.current
    val listState = rememberLazyListState()

    val canScrollUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }

    LaunchedEffect(reporter, canScrollUp) {
        reporter?.setCanScrollUp(canScrollUp)
    }

    content(listState)
}

@Composable
fun LazyGridScrollStateHelper(
    content: @Composable (LazyGridState) -> Unit
) {
    val reporter = LocalAppBarScrollReporter.current
    val gridState = rememberLazyGridState()

    val canScrollUp by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 0
        }
    }

    LaunchedEffect(reporter, canScrollUp) {
        reporter?.setCanScrollUp(canScrollUp)
    }

    content(gridState)
}