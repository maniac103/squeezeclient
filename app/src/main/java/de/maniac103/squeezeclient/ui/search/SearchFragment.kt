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

package de.maniac103.squeezeclient.ui.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.viewModelWithParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.common.ComposeFragment

class SearchFragment : ComposeFragment() {
    interface Listener {
        fun onCloseSearch()
        fun onOpenLocalSearchPage(searchTerm: String, type: LibrarySearchRequest.Mode)
        fun onOpenRadioSearchPage(searchTerm: String)
    }

    private val viewModel by viewModelWithParams {
        val playerId = requireArguments().getParcelable("playerId", PlayerId::class)
        SearchViewModel(requireActivity().application, playerId)
    }
    private val listener get() = requireParentAs<Listener>()

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            listener.onCloseSearch()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            viewModel.setBackProgress(backEvent.progress)
        }

        override fun handleOnBackCancelled() {
            viewModel.setBackProgress(0F)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
    }

    @Composable
    override fun createContent() {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            SearchBox(
                viewModel,
                pillModifier = Modifier
                    .padding(innerPadding),
                onCloseListener = { listener.onCloseSearch() },
                onOpenResultsListener = { query, type ->
                    if (type != null) {
                        listener.onOpenLocalSearchPage(query, type)
                    } else {
                        listener.onOpenRadioSearchPage(query)
                    }
                }
            )
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // MainActivity hides this screen while there is no connection; cancel pending
        // searches in that case, so that no requests are published without a connection.
        if (hidden) {
            viewModel.updateQuery("")
        }
    }

    companion object {
        fun create(playerId: PlayerId) = SearchFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
            }
        }
    }
}

@Composable
fun SearchBox(
    viewModel: SearchViewModel = viewModel(),
    @SuppressLint("ModifierParameter") pillModifier: Modifier = Modifier,
    onCloseListener: () -> Unit,
    onOpenResultsListener: (query: String, type: LibrarySearchRequest.Mode?) -> Unit
) {
    val queryState by viewModel.queryFlow.collectAsState()
    val categories by viewModel.categoriesFlow.collectAsState(emptyList())
    val backProgress by viewModel.backProgressFlow.collectAsState()

    SearchBox(
        queryText = queryState.text,
        submittedQueryText = queryState.submitted,
        categories = categories,
        backgroundAlpha = 1F - backProgress,
        pillModifier = pillModifier.scale(1F - backProgress),
        onQueryTextChanged = { viewModel.updateQuery(it) },
        onClearQuery = { viewModel.updateQuery("") },
        onSubmitQuery = { viewModel.submitQueryImmediately() },
        onClose = onCloseListener,
        onCategoryClick = { category ->
            if (!category.busy) {
                onOpenResultsListener(queryState.text, category.type)
            }
        }
    )
}

@Composable
fun SearchBox(
    queryText: String,
    submittedQueryText: Boolean,
    categories: List<SearchViewModel.Category>,
    backgroundAlpha: Float = 1F,
    @SuppressLint("ModifierParameter") pillModifier: Modifier = Modifier,
    onQueryTextChanged: (String) -> Unit = {},
    onSubmitQuery: () -> Unit = {},
    onClearQuery: () -> Unit = {},
    onClose: () -> Unit = {},
    onCategoryClick: (SearchViewModel.Category) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = 0.7F * backgroundAlpha
        ),
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = { onClose() })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = pillModifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Column {
                    Row {
                        IconButton(
                            onClick = { onClose() },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_left_24dp),
                                contentDescription = null // FIXME
                            )
                        }
                        TextField(
                            value = queryText,
                            placeholder = {
                                Text(stringResource(R.string.search_editor_hint))
                            },
                            onValueChange = { onQueryTextChanged(it) },
                            colors = TextFieldDefaults.colors().copy(
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .onKeyEvent { event ->
                                    if (event.key == Key.Enter) {
                                        onSubmitQuery()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .focusRequester(focusRequester)
                        )
                        if (queryText.isNotEmpty()) {
                            IconButton(
                                onClick = { onClearQuery() },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24dp),
                                    contentDescription = null // FIXME
                                )
                            }
                        }
                    }
                    if (queryText.isNotEmpty() && submittedQueryText) {
                        HorizontalDivider()
                        LazyColumn {
                            items(categories) { category ->
                                SearchResultCountRow(
                                    category = category,
                                    modifier = Modifier
                                        .clickable(onClick = { onCategoryClick(category) })
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCountRow(
    category: SearchViewModel.Category,
    modifier: Modifier = Modifier
) = ListItem(
    colors = ListItemDefaults.colors().copy(
        containerColor = Color.Transparent
    ),
    headlineContent = {
        Text(text = category.title)
    },
    trailingContent = {
        if (category.busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = category.resultCount.toString()
            )
        }
    },
    modifier = modifier
)

@Preview
@Composable
fun SearchBoxPreview() {
    val categories = listOf(
        SearchViewModel.Category("First", null, 15, false),
        SearchViewModel.Category("Second", null, 15, true)
    )
    SearchBox("Search text", true, categories)
}
