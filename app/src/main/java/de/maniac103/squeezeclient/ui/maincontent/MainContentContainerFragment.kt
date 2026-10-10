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

import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.markodevcic.peko.PermissionRequester
import com.markodevcic.peko.PermissionResult
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.extfuncs.await
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.preferences
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.service.DownloadWorker
import de.maniac103.squeezeclient.ui.common.ComposeFragment
import de.maniac103.squeezeclient.ui.slideshow.ImageViewActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class MainContentContainerFragment : ComposeFragment() {
    interface Listener {
        fun onContentScrollStateChanged(canScrollUp: Boolean)
        fun onBreadcrumbsChanged(state: BreadcrumbsState)
        fun openNowPlayingIfNeeded()
    }
    private val listener get() = requireParentAs<Listener>()

    private val viewModel by viewModel<MainContentViewModel> {
        val playerId = requireArguments().getParcelable("playerId", PlayerId::class)
        parametersOf(playerId)
    }

    private val useGridFlow = preferences.forceGridLayout
        .asFlow()
        .map { it || resources.getBoolean(R.bool.force_grid_items_for_lists) }

    @Composable
    override fun createContent() {
        MaterialTheme {
            val contentBottomPadding =
                resources.getDimension(R.dimen.main_content_list_bottom_padding).dp
            val serverConfigState = preferences.serverConfig.asFlow().collectAsState(null)
            val useGridState = useGridFlow.collectAsState(false)

            MainContent(
                viewModel = viewModel,
                serverConfig = serverConfigState,
                useGridForSlimbrowseItems = useGridState,
                bottomPagePadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
                onScrollStateChanged = { canScrollUp ->
                    listener.onContentScrollStateChanged(canScrollUp)
                },
                onBreadcrumbsChanged = { state ->
                    listener.onBreadcrumbsChanged(state)
                },
                onGalleryItemSelected = { image ->
                    val intent = ImageViewActivity.createIntent(requireContext(), image)
                    startActivity(intent)
                },
                onDownloadSelected = { request ->
                    triggerDownload(request)
                },
                onWebLinkSelected = { _, link ->
                    val intent = CustomTabsIntent.Builder()
                        .build()
                    intent.launchUrl(requireContext(), link)
                },
                onGoToNowPlaying = {
                    listener.openNowPlayingIfNeeded()
                }
            )
        }
    }

    fun goToHome() {
        viewModel.goToHome()
    }

    fun goBack(levels: Int) {
        viewModel.goBack(levels)
    }

    fun openLocalSearchResults(searchTerm: String, type: LibrarySearchRequest.Mode) {
        viewModel.openLocalSearchResults(searchTerm, type)
    }

    fun openRadioSearchResults(searchTerm: String) {
        viewModel.openRadioSearchResults(searchTerm)
    }

    fun onNowPlayingContextMenuAction(
        action: JiveAction,
        parentItem: SlimBrowseItemList.SlimBrowseItem,
        contextItem: SlimBrowseItemList.SlimBrowseItem
    ) = viewModel.onNowPlayingContextMenuAction(action, contextItem, parentItem)

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
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.download_permission_rationale_message)
            .create()
            .await(
                positiveText = getString(R.string.permission_rationale_allow),
                negativeText = getString(R.string.download_permission_rationale_cancel)
            )
        if (!dialogResult) {
            return false
        }
        return requester.request(android.Manifest.permission.POST_NOTIFICATIONS)
            .first() is PermissionResult.Granted
    }

    companion object {
        fun create(playerId: PlayerId) = MainContentContainerFragment().apply {
            arguments = Bundle().apply {
                putParcelable("playerId", playerId)
            }
        }
    }
}

data class PageTitleInfo(val title: List<String>, val icon: Drawable?)