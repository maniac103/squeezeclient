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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.viewModelWithParams
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.Theme
import kotlin.getValue
import kotlinx.coroutines.Job

class ItemActionsMenuSheet : BottomSheetDialogFragment() {
    interface Listener {
        fun onActionSelected(action: JiveAction, item: SlimBrowseItemList.SlimBrowseItem): Job?
        fun onDownloadSelected(data: DownloadRequestData): Job?
    }

    private val viewModel by viewModelWithParams {
        val item = requireArguments().getParcelable(
            "item",
            SlimBrowseItemList.SlimBrowseItem::class
        )
        ItemActionsViewModel(requireActivity().application, item)
    }
    private val listener get() = requireParentAs<Listener>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme {
                ItemActionsSheet { action ->
                    val job = if (action.download != null) {
                        listener.onDownloadSelected(action.download)
                    } else {
                        listener.onActionSelected(requireNotNull(action.action), viewModel.item)
                    }
                    if (job != null) {
                        viewModel.setActionBusy(action, job)
                        job.invokeOnCompletion {
                           if (isAdded) {
                               dismissAllowingStateLoss()
                           }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun create(item: SlimBrowseItemList.SlimBrowseItem) = ItemActionsMenuSheet().apply {
            arguments = Bundle().apply {
                putParcelable("item", item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    viewModel: ItemActionsViewModel = viewModel(),
    actionSelectedListener: (ItemActionsViewModel.ActionItem) -> Unit
) {
    val header by viewModel.headerFlow.collectAsState(null)
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
                header?.imageRequest?.let {
                    AsyncImage(
                        it,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    header?.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    header?.subText
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            Text(
                                text = it,
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