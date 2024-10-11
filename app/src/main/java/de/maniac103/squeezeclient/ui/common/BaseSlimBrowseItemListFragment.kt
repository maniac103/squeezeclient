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

package de.maniac103.squeezeclient.ui.common

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.markodevcic.peko.PermissionRequester
import com.markodevcic.peko.PermissionResult
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.await
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.showActionTimePicker
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.service.DownloadWorker
import de.maniac103.squeezeclient.ui.bottomsheets.ChoicesBottomSheetFragment
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetFragment
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetFragment
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsMenuSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

abstract class BaseSlimBrowseItemListFragment :
    BasePagingListFragment<SlimBrowseItemList.SlimBrowseItem, SlimBrowseItemListViewHolder>(),
    SlimBrowseItemListAdapter.ItemSelectionListener,
    ChoicesBottomSheetFragment.SelectionListener,
    ContextMenuBottomSheetFragment.Listener,
    ItemActionsMenuSheet.Listener,
    InputBottomSheetFragment.ItemSubmitListener {

    interface NavigationListener {
        fun onOpenSubItemList(item: SlimBrowseItemList.SlimBrowseItem, itemFetchAction: JiveAction)
        fun onOpenWebLink(title: String, link: Uri)
        fun onGoAction(
            title: String,
            actionTitle: String?,
            action: JiveAction,
            nextWindow: SlimBrowseItemList.NextWindow?
        ): Job?
        fun onHandleMultiLevelRefresh(levels: Int)
    }

    protected abstract val playerId: PlayerId
    protected abstract val title: String
    protected abstract val showIcons: Boolean
    protected open val fetchAction: JiveAction? = null

    override val titleFlow get() = flowOf(title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.enableMainContentBackground()
    }

    override fun onCreateAdapter(
        diffCallback: DiffUtil.ItemCallback<SlimBrowseItemList.SlimBrowseItem>
    ): PagingDataAdapter<SlimBrowseItemList.SlimBrowseItem, SlimBrowseItemListViewHolder> {
        val adapter = SlimBrowseItemListAdapter(diffCallback, showIcons, useGrid)
        adapter.itemSelectionListener = this
        return adapter
    }

    override fun areItemsTheSame(
        lhs: SlimBrowseItemList.SlimBrowseItem,
        rhs: SlimBrowseItemList.SlimBrowseItem
    ): Boolean {
        return lhs.listPosition == rhs.listPosition
    }

    override fun areItemContentsTheSame(
        lhs: SlimBrowseItemList.SlimBrowseItem,
        rhs: SlimBrowseItemList.SlimBrowseItem
    ): Boolean {
        return lhs == rhs
    }

    override fun onItemSelected(item: SlimBrowseItemList.SlimBrowseItem): Job? {
        val listener = activity as? NavigationListener ?: return null
        val actions = item.actions ?: return null
        return when {
            actions.input != null -> {
                showInput(item)
                null
            }
            actions.choices != null -> {
                showChoices(item)
                null
            }
            actions.checkbox != null -> {
                val action = if (actions.checkbox.state) {
                    actions.checkbox.offAction
                } else {
                    actions.checkbox.onAction
                }
                executeAction(
                    action,
                    item.nextWindow ?: SlimBrowseItemList.NextWindow.RefreshSelf,
                    actions.onClickRefresh
                )
            }
            actions.radio != null -> {
                executeAction(
                    actions.radio.action,
                    item.nextWindow ?: SlimBrowseItemList.NextWindow.RefreshSelf,
                    actions.onClickRefresh
                )
            }
            item.webLink != null -> {
                listener.onOpenWebLink(item.title, item.webLink.toUri())
                null
            }
            item.subItems != null -> {
                fetchAction?.let { listener.onOpenSubItemList(item, it) }
                null
            }
            actions.goAction != null -> {
                val nextWindow = actions.goAction.nextWindow ?: item.nextWindow
                listener.onGoAction(item.title, null, actions.goAction, nextWindow)
            }
            else -> null
        }
    }

    override fun onContextMenu(item: SlimBrowseItemList.SlimBrowseItem): Job? {
        val actions = item.actions ?: return null
        return when {
            actions.moreAction != null -> lifecycleScope.launch {
                val itemList = loadContextMenuItems(actions.moreAction, actions)
                val f = ContextMenuBottomSheetFragment.create(playerId, item, itemList)
                f.show(childFragmentManager, "contextmenu")
            }
            actions.hasContextMenu -> {
                val f = ItemActionsMenuSheet.create(item)
                f.show(childFragmentManager, "itemactions")
                null
            }
            else -> null
        }
    }

    override fun onChoiceSelected(choice: JiveAction, refresh: JiveActions.RefreshBehavior?) =
        executeAction(choice, SlimBrowseItemList.NextWindow.RefreshSelf, refresh)

    override fun onInputSubmitted(
        item: SlimBrowseItemList.SlimBrowseItem,
        action: JiveAction,
        isGoAction: Boolean
    ) = if (isGoAction) {
        val listener = activity as? NavigationListener
        listener?.onGoAction(title, null, action, item.nextWindow)
    } else {
        executeAction(action, item.nextWindow, item.actions?.onClickRefresh)
    }

    override fun onContextItemSelected(
        parentTitle: String,
        item: SlimBrowseItemList.SlimBrowseItem
    ): Job? {
        val listener = activity as? NavigationListener
        val actions = item.actions ?: return null

        val translateNextWindow = { action: JiveAction ->
            when (val nextWindow = action.nextWindow ?: item.nextWindow) {
                SlimBrowseItemList.NextWindow.RefreshSelf -> null
                SlimBrowseItemList.NextWindow.ParentWithRefresh ->
                    SlimBrowseItemList.NextWindow.RefreshSelf
                SlimBrowseItemList.NextWindow.Parent -> null
                SlimBrowseItemList.NextWindow.GrandParent ->
                    SlimBrowseItemList.NextWindow.Parent
                else -> nextWindow
            }
        }

        return when {
            // FIXME: the item title check is kinda ugly, but there's no proper way to
            // implement a marker object it seems, since SlimBrowseItem is a data class
            actions.downloadData != null && item.title == getString(R.string.action_download) ->
                triggerDownload(actions.downloadData)
            actions.doAction != null -> {
                val nextWindow = translateNextWindow(actions.doAction)
                executeAction(actions.doAction, nextWindow, null)
            }
            actions.goAction != null ->
                when (actions.goAction.nextWindow ?: item.nextWindow) {
                    SlimBrowseItemList.NextWindow.Parent -> lifecycleScope.launch {
                        // next target is our list, and we shall not refresh
                        connectionHelper.executeAction(playerId, actions.goAction)
                    }
                    SlimBrowseItemList.NextWindow.ParentWithRefresh -> {
                        // next target is our list, and we shall refresh
                        executeAction(
                            actions.goAction,
                            null,
                            JiveActions.RefreshBehavior.RefreshSelf
                        )
                    }
                    else -> {
                        listener?.onGoAction(
                            parentTitle,
                            item.title,
                            actions.goAction,
                            translateNextWindow(actions.goAction)
                        )
                    }
                }
            else -> null
        }
    }

    override fun onActionSelected(action: JiveAction): Job? = executeAction(action, null, null)
    override fun onDownloadSelected(data: DownloadRequestData) = triggerDownload(data)

    private fun showInput(item: SlimBrowseItemList.SlimBrowseItem) {
        val input = requireNotNull(item.actions?.input)
        if (input.type == JiveActions.Input.Type.Time) {
            showActionTimePicker(playerId, item.title, input)
        } else {
            val f = InputBottomSheetFragment.createForItem(item, input)
            f.show(childFragmentManager, "input")
        }
    }

    private fun showChoices(item: SlimBrowseItemList.SlimBrowseItem) {
        val choices = item.actions?.choices ?: return
        val f = ChoicesBottomSheetFragment.create(item.title, choices, item.actions.onClickRefresh)
        f.show(childFragmentManager, "choices")
    }

    private fun executeAction(
        action: JiveAction,
        nextWindow: SlimBrowseItemList.NextWindow?,
        refresh: JiveActions.RefreshBehavior?
    ) = lifecycleScope.launch {
        connectionHelper.executeAction(playerId, action)
        when (action.nextWindow ?: nextWindow) {
            SlimBrowseItemList.NextWindow.RefreshSelf,
            SlimBrowseItemList.NextWindow.ParentWithRefresh ->
                refresh()
            else -> {}
        }
        val listener = activity as? NavigationListener
        when (refresh) {
            JiveActions.RefreshBehavior.RefreshSelf ->
                refresh()
            JiveActions.RefreshBehavior.RefreshParent ->
                listener?.onHandleMultiLevelRefresh(1)
            JiveActions.RefreshBehavior.RefreshGrandParent ->
                listener?.onHandleMultiLevelRefresh(2)
            else -> {}
        }
    }

    private suspend fun loadContextMenuItems(
        action: JiveAction,
        actions: JiveActions
    ): List<SlimBrowseItemList.SlimBrowseItem> {
        val loadedItems = connectionHelper.fetchItemsForAction(playerId, action, PagingParams.All)
        return if (actions.downloadData == null) {
            loadedItems.items
        } else {
            loadedItems.items.toMutableList().apply {
                val item = SlimBrowseItemList.SlimBrowseItem(
                    size,
                    getString(R.string.action_download),
                    null, null, null, null, null, null,
                    actions,
                    null, null, null
                )
                add(item)
            }
        }
    }

    private fun triggerDownload(data: DownloadRequestData) = lifecycleScope.launch {
        if (!requestNotificationPermissionForDownload()) {
            return@launch
        }
        val items = connectionHelper.fetchSongInfosForDownload(data)
        val request = DownloadWorker.buildRequest(requireContext(), items)
        WorkManager.getInstance(requireContext()).enqueue(request)
    }

    private suspend fun requestNotificationPermissionForDownload(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Don't need runtime permission in that case
            return true
        }
        val requester = PermissionRequester.instance()
        if (requester.isAnyGranted(android.Manifest.permission.POST_NOTIFICATIONS)) {
            // Permission already granted
            return true
        }

        when (requester.request(android.Manifest.permission.POST_NOTIFICATIONS).first()) {
            is PermissionResult.Denied.NeedsRationale -> {}
            is PermissionResult.Denied.DeniedPermanently -> return false
            is PermissionResult.Cancelled -> return false
            is PermissionResult.Granted -> return true
        }
        // When we're here, we need to show a rationale dialog
        val dialogResult = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.download_permission_rationale_title)
            .setMessage(R.string.download_permission_rationale_message)
            .create()
            .await(
                positiveText = getString(R.string.download_permission_rationale_allow),
                negativeText = getString(R.string.download_permission_rationale_cancel)
            )
        if (!dialogResult) {
            return false
        }
        return requester.request(android.Manifest.permission.POST_NOTIFICATIONS)
            .first() is PermissionResult.Granted
    }
}
