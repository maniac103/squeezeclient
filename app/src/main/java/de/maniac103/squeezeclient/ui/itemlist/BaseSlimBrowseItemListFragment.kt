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

package de.maniac103.squeezeclient.ui.itemlist

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.markodevcic.peko.PermissionRequester
import com.markodevcic.peko.PermissionResult
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.FragmentGenericListBinding
import de.maniac103.squeezeclient.extfuncs.await
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.showActionTimePicker
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.service.DownloadWorker
import de.maniac103.squeezeclient.ui.MainContentChild
import de.maniac103.squeezeclient.ui.bottomsheets.ChoicesBottomSheetFragment
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetFragment
import de.maniac103.squeezeclient.ui.common.BasePagingListFragment
import de.maniac103.squeezeclient.ui.common.SlimBrowseItemListAdapter
import de.maniac103.squeezeclient.ui.common.SlimBrowseItemListViewHolder
import de.maniac103.squeezeclient.ui.contextmenu.ContextMenuBottomSheetFragment
import de.maniac103.squeezeclient.ui.contextmenu.ItemActionsMenuSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

abstract class BaseSlimBrowseItemListFragment :
    BasePagingListFragment<SlimBrowseItemList.SlimBrowseItem, SlimBrowseItemListViewHolder>(),
    MainContentChild,
    SlimBrowseItemListAdapter.ItemSelectionListener,
    ChoicesBottomSheetFragment.SelectionListener,
    ContextMenuBottomSheetFragment.Listener,
    ItemActionsMenuSheet.Listener,
    InputBottomSheetFragment.ItemSubmitListener {

    interface NavigationListener {
        fun onOpenSubItemList(item: SlimBrowseItemList.SlimBrowseItem, itemFetchAction: JiveAction)
        fun onOpenWebLink(title: String, link: Uri)
        fun onHandleDoOrGoAction(
            action: JiveAction,
            isGoAction: Boolean,
            item: SlimBrowseItemList.SlimBrowseItem,
            parentItem: SlimBrowseItemList.SlimBrowseItem?
        ): Job?
    }

    protected abstract val playerId: PlayerId
    protected abstract val showIcons: Boolean
    protected open val fetchAction: JiveAction? = null

    override val scrollingTargetView get() = binding.recycler
    private val listener get() = requireParentAs<NavigationListener>()

    override fun onBindingCreated(binding: FragmentGenericListBinding) {
        super.onBindingCreated(binding)
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

    // SlimBrowseItemListAdapter.ItemSelectionListener implementation

    override fun onItemSelected(item: SlimBrowseItemList.SlimBrowseItem): Job? {
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
                listener.onHandleDoOrGoAction(action, false, item, null)
            }
            actions.radio != null -> {
                listener.onHandleDoOrGoAction(actions.radio.action, false, item, null)
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
                listener.onHandleDoOrGoAction(actions.goAction, true, item, null)
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

    // ChoicesBottomSheetFragment.SelectionListener implementation

    override fun onChoiceSelected(choice: JiveAction, extraData: Bundle?): Job? {
        val item = requireNotNull(extraData)
            .getParcelable("item", SlimBrowseItemList.SlimBrowseItem::class)
        return listener.onHandleDoOrGoAction(choice, false, item, null)
    }

    // InputBottomSheetFragment.ItemSubmitListeber implementation

    override fun onInputSubmitted(
        item: SlimBrowseItemList.SlimBrowseItem,
        action: JiveAction,
        isGoAction: Boolean
    ) = listener.onHandleDoOrGoAction(action, isGoAction, item, null)

    // ContextMenuBottomSheetFragment.Listener implementation

    override fun onContextItemSelected(
        parentItem: SlimBrowseItemList.SlimBrowseItem,
        selectedItem: SlimBrowseItemList.SlimBrowseItem
    ): Job? {
        val actions = selectedItem.actions ?: return null

        return when {
            // FIXME: the item title check is kinda ugly, but there's no proper way to
            // implement a marker object it seems, since SlimBrowseItem is a data class
            actions.downloadData != null &&
                selectedItem.title == getString(R.string.action_download) ->
                triggerDownload(actions.downloadData)
            actions.doAction != null ->
                listener.onHandleDoOrGoAction(actions.doAction, false, parentItem, selectedItem)
            actions.goAction != null ->
                listener.onHandleDoOrGoAction(actions.goAction, true, parentItem, selectedItem)
            else -> null
        }
    }

    // ItemActionsMenuSheet.Listener implementation

    override fun onActionSelected(action: JiveAction, item: SlimBrowseItemList.SlimBrowseItem) =
        listener.onHandleDoOrGoAction(action, false, item, null)
    override fun onDownloadSelected(data: DownloadRequestData) = triggerDownload(data)

    // Private implementation details

    private fun showInput(item: SlimBrowseItemList.SlimBrowseItem) {
        val input = requireNotNull(item.actions?.input)
        if (input.type == JiveActions.Input.Type.Time) {
            showActionTimePicker(item.title, input) {
                onInputSubmitted(item, input.action.withInputValue(it), false)
            }
        } else {
            val f = InputBottomSheetFragment.createForItem(item, input)
            f.show(childFragmentManager, "input")
        }
    }

    private fun showChoices(item: SlimBrowseItemList.SlimBrowseItem) {
        val choices = item.actions?.choices ?: return
        val extraData = bundleOf("item" to item)
        val f = ChoicesBottomSheetFragment.create(item.title, choices, extraData)
        f.show(childFragmentManager, "choices")
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
                // Create a fake slimbrowse item for the download, which has some constraints:
                // - Must contain a go action (otherwise it's assumed to be non-clickable)
                // - Go action must not point to a context menu
                // - Download data must match that of the base item
                val item = SlimBrowseItemList.SlimBrowseItem(
                    listPosition = size,
                    title = getString(R.string.action_download),
                    subText = null,
                    textKey = null,
                    type = null,
                    trackType = null,
                    icon = null,
                    iconId = null,
                    actions = JiveActions(
                        goAction = JiveAction(emptyList(), emptyMap(), null, null),
                        doAction = null,
                        moreAction = null,
                        playAction = null,
                        addAction = null,
                        insertAction = null,
                        downloadData = actions.downloadData,
                        checkbox = null,
                        choices = null,
                        radio = null,
                        input = null,
                        slider = null,
                        onClickRefresh = null
                    ),
                    nextWindow = null,
                    subItems = null,
                    webLink = null
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
        DownloadWorker.enqueue(requireContext(), items)
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
