
/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
 *
 * Portions adapted from SqueezeDrive, Copyright (c) 2026 SqueezeDrive contributors.

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

package de.maniac103.squeezeclient.service.mediasession

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItem
import de.maniac103.squeezeclient.ui.itemlist.JiveUiItemIcon
import de.maniac103.squeezeclient.ui.itemlist.toUiItem
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(UnstableApi::class)
class LibraryTree(
    private val context: Context,
    private val connectionHelper: ConnectionHelper,
    private val preferences: Preferences,
    private val subscriptions: LibrarySubscriptions
) {
    var connectedPlayer: PlayerId? = null
        set(value) {
            if (field != value) {
                field = value
                subscriptions.notifyChange()
            }
        }

    fun rootItem(): MediaItem = folderItem(
        node = LibraryTreeNode.Root,
        title = context.getString(R.string.app_name),
        iconResId = R.mipmap.ic_launcher
    )

    suspend fun getChildren(
        parentId: String,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        val node = parentId.toTreeNode()
            ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)

        val playerId = connectedPlayer
            ?: return if (node == LibraryTreeNode.Root) {
                val noServerInfo = infoItem(
                    titleResId = R.string.browse_no_player_title,
                    subTitleResId = R.string.browse_no_player_subtitle
                )
                LibraryResult.ofItemList(listOf(noServerInfo), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_SESSION_DISCONNECTED)
            }

        return when (node) {
            LibraryTreeNode.Root -> LibraryResult.ofItemList(rootChildren(), null)

            LibraryTreeNode.Favorites ->
                homeMenuChildren(
                    playerId = playerId,
                    id = "favorites",
                    page = page,
                    pageSize = pageSize
                )

            LibraryTreeNode.Library ->
                homeMenuChildren(
                    playerId = playerId,
                    id = "myMusic",
                    page = page,
                    pageSize = pageSize,
                    childIdMatcher = { id ->
                        id.startsWith("myMusic") && id != "myMusicSearch"
                    }
                )

            LibraryTreeNode.Radio ->
                homeMenuChildren(
                    playerId = playerId,
                    id = "radios",
                    page = page,
                    pageSize = pageSize,
                    childIdMatcher = { id -> id != "opmlsearch" }
                )

            LibraryTreeNode.RandomPlay ->
                homeMenuChildren(
                    playerId = playerId,
                    id = "randomplay",
                    page = page,
                    pageSize = pageSize
                )

            is LibraryTreeNode.BrowseItem -> {
                if (node.go == null) {
                    LibraryResult.ofItemList(emptyList(), null)
                } else {
                    browseChildren(playerId, node.go, node, page, pageSize)
                }
            }

            is LibraryTreeNode.HomeMenuItem ->
                homeMenuChildren(
                    playerId = playerId,
                    id = node.id,
                    page = page,
                    pageSize = pageSize
                )
        }
    }

    fun getItem(mediaId: String): LibraryResult<MediaItem> {
        val node = mediaId.toTreeNode()
            ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val item = when (node) {
            LibraryTreeNode.Root -> rootItem()
            LibraryTreeNode.Favorites -> favoritesItem()
            LibraryTreeNode.Library -> libraryItem()
            LibraryTreeNode.Radio -> radioItem()
            LibraryTreeNode.RandomPlay -> randomPlayItem()
            else -> synthesizeItem(node)
        }
        return LibraryResult.ofItem(item, null)
    }

    suspend fun getSearchResult(
        query: String,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        val playerId = connectedPlayer
            ?: return LibraryResult.ofError(SessionError.ERROR_SESSION_DISCONNECTED)

        val result = runCatching {
            searchItems(playerId, query, page, pageSize)
        }
        return result.fold(
            onSuccess = { LibraryResult.ofItemList(it, null) },
            onFailure = { LibraryResult.ofError(SessionError.ERROR_IO) }
        )
    }

    suspend fun processSetMediaItems(
        inputItems: List<MediaItem>,
        startIndex: Int
    ): MediaSession.MediaItemsWithStartPosition {
        val playerId = connectedPlayer
        if (playerId == null || inputItems.isEmpty()) {
            return MediaSession.MediaItemsWithStartPosition(
                emptyList(),
                0,
                C.TIME_UNSET
            )
        }

        // Execute the selected play action before appending any other requested items:
        // a play action can replace the server queue and would erase earlier appends.
        val orderedItems = if (startIndex > 0) {
            buildList {
                add(inputItems[startIndex])
                addAll(inputItems.filterIndexed { index, _ -> index != startIndex })
            }
        } else {
            inputItems
        }

        val resolvedItems = handleIncomingItems(playerId, orderedItems, true)
        return MediaSession.MediaItemsWithStartPosition(resolvedItems, 0, C.TIME_UNSET)
    }

    suspend fun processAddMediaItems(inputItems: List<MediaItem>): List<MediaItem> {
        val playerId = connectedPlayer
            ?: return emptyList()
        return handleIncomingItems(playerId, inputItems, false)
    }

    private suspend fun handleIncomingItems(
        playerId: PlayerId,
        items: List<MediaItem>,
        playFirstItem: Boolean
    ) = items.mapIndexedNotNull { index, item ->
        val node = item.mediaId.toTreeNode() as? LibraryTreeNode.BrowseItem
        val action = if (playFirstItem && index == 0) node?.play else node?.add
        if (action != null) {
            connectionHelper.executeAction(playerId, action)
        }
        node?.let { synthesizeItem(it) }
    }

    private fun synthesizeItem(node: LibraryTreeNode): MediaItem {
        val title = when (node) {
            is LibraryTreeNode.HomeMenuItem -> node.title
            is LibraryTreeNode.BrowseItem -> node.title
            else -> throw IllegalArgumentException()
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(node.browsable)
            .setIsPlayable(node.playable)
            .build()
        return MediaItem.Builder()
            .setMediaId(node.toMediaId())
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun browseChildren(
        playerId: PlayerId,
        goAction: JiveAction,
        parentNode: LibraryTreeNode.BrowseItem?,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        val count = pageSize.coerceAtMost(MAX_BROWSE_PAGE)
        val listResult = runCatching {
            connectionHelper.fetchItemsForAction(
                playerId = playerId,
                action = goAction,
                page = PagingParams(page * pageSize, count),
                fetchExtraInfoIfPossible = true
            )
        }
        return listResult.fold(
            onSuccess = { list ->
                val items = buildList {
                    if (parentNode?.play != null) {
                        add(playItem(parentNode))
                    }
                    addAll(list.items.map { browseItem(it) })
                }
                LibraryResult.ofItemList(items, null)
            },
            onFailure = {
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        )
    }

    private suspend fun homeMenuChildren(
        playerId: PlayerId,
        id: String,
        page: Int,
        pageSize: Int,
        childIdMatcher: (String) -> Boolean = { true }
    ): LibraryResult<ImmutableList<MediaItem>> {
        val homeMenu = withTimeoutOrNull(HOME_MENU_TIMEOUT) {
            val playerState = connectionHelper.playerState(playerId).first()
            playerState.homeMenu.first()
        }
        if (homeMenu == null) {
            return LibraryResult.ofError(SessionError.ERROR_SESSION_DISCONNECTED)
        }
        val fetchAction = homeMenu[id]?.actions?.goAction
        val childItems = homeMenu.values
            .filter { it.node == id && childIdMatcher(it.id) }
            .sortedBy { it.sortWeight }

        return when {
            childItems.isNotEmpty() -> {
                val items = childItems.map { child ->
                    val hasChildren = homeMenu.values.any { it.node == child.id }
                    homeMenuItem(child, hasChildren)
                }
                LibraryResult.ofItemList(items, null)
            }

            fetchAction != null -> {
                browseChildren(playerId, fetchAction, null, page, pageSize)
            }

            else -> {
                LibraryResult.ofItemList(emptyList(), null)
            }
        }
    }

    suspend fun searchCount(query: String): Int {
        val counts = connectionHelper.getLocalLibrarySearchResultCounts(query)
        return counts.artists + counts.albums + counts.tracks + counts.genres
    }

    private suspend fun searchItems(
        playerId: PlayerId,
        query: String,
        page: Int,
        pageSize: Int
    ): List<MediaItem> {
        val counts = connectionHelper.getLocalLibrarySearchResultCounts(query)
        val categories = listOf(
            LibrarySearchRequest.Mode.Artist() to counts.artists,
            LibrarySearchRequest.Mode.Albums() to counts.albums,
            LibrarySearchRequest.Mode.Tracks() to counts.tracks,
            LibrarySearchRequest.Mode.Genres() to counts.genres
        )

        var offset = page * pageSize
        var remaining = pageSize.coerceAtMost(MAX_BROWSE_PAGE)

        return buildList {
            for ((mode, total) in categories) {
                if (remaining == 0) {
                    break
                }
                if (offset >= total) {
                    offset -= total
                    continue
                }

                val count = remaining.coerceAtMost(total - offset)
                val list = connectionHelper.getLocalLibrarySearchResults(
                    playerId = playerId,
                    searchTerm = query,
                    type = mode,
                    page = PagingParams(offset, count)
                )
                addAll(list.items.map { browseItem(it) })
                remaining -= list.items.size
                offset = 0
            }
        }
    }

    private fun favoritesItem() = folderItem(
        LibraryTreeNode.Favorites,
        context.getString(R.string.browse_favorites),
        R.drawable.hm_favorites
    )

    private fun libraryItem() = folderItem(
        LibraryTreeNode.Library,
        context.getString(R.string.browse_library),
        R.drawable.hm_mymusic
    )

    private fun radioItem() = folderItem(
        LibraryTreeNode.Radio,
        context.getString(R.string.browse_radio),
        R.drawable.hm_radios
    )

    private fun randomPlayItem() = folderItem(
        LibraryTreeNode.RandomPlay,
        context.getString(R.string.browse_random_play),
        R.drawable.hm_randomplay
    )

    private fun rootChildren() = listOf(
        libraryItem(),
        radioItem(),
        favoritesItem(),
        randomPlayItem()
    )

    private fun infoItem(titleResId: Int, subTitleResId: Int): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(context.getString(titleResId))
            .setSubtitle(context.getString(subTitleResId))
            .setIsBrowsable(false)
            .setIsPlayable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId("info-$titleResId")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun folderItem(node: LibraryTreeNode, title: String, iconResId: Int): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtworkUri(iconResourceUri(iconResId))
            .setIsBrowsable(node.browsable)
            .setIsPlayable(node.playable)
            .build()
        return MediaItem.Builder()
            .setMediaId(node.toMediaId())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun homeMenuItem(item: JiveHomeMenuItem, hasChildren: Boolean): MediaItem {
        val uiItem = item.toUiItem().uiItem
        val browsable = when {
            hasChildren -> true
            item.actions?.goAction != null -> item.nextWindow == null
            else -> false
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(uiItem.title)
            .setSubtitle(uiItem.subText)
            .setArtworkUri(iconUri(uiItem.icon))
            .setIsBrowsable(browsable)
            .setIsPlayable(item.actions?.playAction != null)
            .build()
        val node = if (hasChildren) {
            LibraryTreeNode.HomeMenuItem(item.id, item.title)
        } else {
            LibraryTreeNode.BrowseItem(
                title = uiItem.title,
                go = item.actions?.goAction,
                play = item.actions?.playAction,
                add = item.actions?.addAction,
                insert = item.actions?.insertAction
            )
        }
        return MediaItem.Builder()
            .setMediaId(node.toMediaId())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun browseItem(item: SlimBrowseItem): MediaItem {
        val uiItem = item.toUiItem().uiItem
        val actions = item.actions
        val node = LibraryTreeNode.BrowseItem(
            title = item.title,
            go = item.actions.goAction,
            play = item.actions.playAction,
            add = item.actions.addAction,
            insert = item.actions.insertAction
        )
        val metadata = MediaMetadata.Builder()
            .setTitle(uiItem.title)
            .setSubtitle(uiItem.subText)
            .setArtworkUri(iconUri(uiItem.icon))
            .setIsBrowsable(actions.goAction != null)
            .setIsPlayable(actions.goAction == null && actions.playAction != null)
            .build()
        return MediaItem.Builder()
            .setMediaId(node.toMediaId())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun playItem(node: LibraryTreeNode.BrowseItem): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(context.getString(R.string.browse_play_all))
            .setSubtitle(node.title)
            .setArtworkUri(iconResourceUri(R.drawable.ic_play_24dp))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(node.toMediaId())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun iconUri(icon: JiveUiItemIcon?) = when (icon) {
        is JiveUiItemIcon.Icon ->
            iconResourceUri(icon.iconResId)

        is JiveUiItemIcon.Artwork -> {
            val serverConfig = preferences.serverConfig.value
            icon.artwork.extractIconUrl()
                ?.let { serverConfig?.url?.resolve(it)?.toString() ?: it }
                ?.toUri()
        }

        else -> null
    }

    private fun iconResourceUri(iconResId: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(iconResId.toString())
        .build()

    companion object {
        private const val MAX_BROWSE_PAGE = 500
        private val HOME_MENU_TIMEOUT = 5.seconds
    }
}
