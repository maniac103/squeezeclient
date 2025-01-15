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

package de.maniac103.squeezeclient.ui

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.BackEventCompat
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.databinding.FragmentMainlistcontainerBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.imageCacheContains
import de.maniac103.squeezeclient.extfuncs.loadArtwork
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.serverMightCacheResults
import de.maniac103.squeezeclient.model.ArtworkItem
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import de.maniac103.squeezeclient.ui.bottomsheets.InfoBottomSheet
import de.maniac103.squeezeclient.ui.bottomsheets.SliderBottomSheetFragment
import de.maniac103.squeezeclient.ui.common.BasePagingListFragment
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import de.maniac103.squeezeclient.ui.itemlist.BaseSlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeListItemFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseSubItemListFragment
import de.maniac103.squeezeclient.ui.search.LibrarySearchResultsFragment
import de.maniac103.squeezeclient.ui.search.RadioSearchResultsFragment
import de.maniac103.squeezeclient.ui.slideshow.GalleryFragment
import java.lang.IllegalStateException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

interface MainContentChild {
    val scrollingTargetView: View
    val titleFlow: Flow<List<String>>
    val iconFlow: Flow<ArtworkItem?>
}

class MainContentContainerFragment :
    ViewBindingFragment<FragmentMainlistcontainerBinding>(
        FragmentMainlistcontainerBinding::inflate
    ),
    JiveHomeListItemFragment.NavigationListener,
    BaseSlimBrowseItemListFragment.NavigationListener,
    SliderBottomSheetFragment.ChangeListener,
    FragmentManager.OnBackStackChangedListener {

    interface Listener {
        fun onScrollTargetChanged(scrollTarget: View?)
        fun onContentStackChanged(
            titles: List<PageTitleInfo>,
            pendingTitle: PageTitleInfo?,
            pendingProgress: Float
        )
        fun openNowPlayingIfNeeded()
    }

    private enum class ReplacementMode {
        SetAsHome,
        OnTopOfHome,
        OnTopOfStack
    }

    private val listener get() = requireParentAs<Listener>()
    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)

    data class PendingBackInfo(
        val fragment: Fragment,
        val tag: String?,
        var progress: Float = 0F
    )

    private var titleSubscription: Job? = null
    private var contentTitles = emptyMap<String, PageTitleInfo>()
    private var pendingBack: PendingBackInfo? = null
    private var pendingNavigation: Job? = null
    private var homeMenu: Map<String, JiveHomeMenuItem> = mapOf()

    // Public methods

    fun handleGoAction(
        item: SlimBrowseItemList.SlimBrowseItem,
        parentItem: SlimBrowseItemList.SlimBrowseItem?,
        action: JiveAction
    ) = handleGoAction(item.title, item, parentItem, action)

    fun goToHome() = childFragmentManager.apply {
        while (backStackEntryCount > 0) {
            popBackStackImmediate()
        }
    }

    fun openLocalSearchResults(searchTerm: String, type: LibrarySearchRequest.Mode) {
        val f = LibrarySearchResultsFragment.create(playerId, type, searchTerm)
        replaceContent(f, "localsearch-$searchTerm", ReplacementMode.OnTopOfHome)
    }

    fun openRadioSearchResults(searchTerm: String) {
        val f = RadioSearchResultsFragment.create(playerId, searchTerm)
        replaceContent(f, "radiosearch-$searchTerm", ReplacementMode.OnTopOfHome)
    }

    // JiveHomeListItemFragment.NavigationListener implementation

    override fun onNodeSelected(nodeId: String) {
        if (homeMenu.values.any { it.node == nodeId }) {
            val f = JiveHomeListItemFragment.create(playerId, nodeId)
            val mode = when (nodeId) {
                "home" -> ReplacementMode.SetAsHome
                else -> ReplacementMode.OnTopOfStack
            }
            replaceContent(f, "home:$nodeId", mode)
        }
    }

    override fun onGoAction(title: String, action: JiveAction): Job? =
        handleGoAction(title, null, null, action)

    // BaseSlimBrowseItemListFragment.NavigationListener implementation

    override fun onHandleDoOrGoAction(
        action: JiveAction,
        isGoAction: Boolean,
        item: SlimBrowseItemList.SlimBrowseItem,
        parentItem: SlimBrowseItemList.SlimBrowseItem?
    ): Job? {
        // The logic below both translates nextWindow to adjust for whether it came from a
        // context menu or not, and splits navigation and refresh which are combined in the
        // protocol. Since it's a little hard to understand, here are the translation tables:
        //
        // nextWindow           without parent item         with parent item
        // Home                 keep                        keep
        // Parent               keep (go up 1 level)        discard (navigate to parent of menu)
        // GrandParent          keep (go up 2 levels)       adjust (go up 1 level)
        // NowPlaying           keep                        keep
        // RefreshSelf          keep (refresh 1 level)      discard (no need to refresh menu)
        // MyMusic              keep                        keep
        // ParentWithRefresh    keep (go up 1, refresh 2)   adjust (refresh 1 level)
        // Presets              keep                        keep
        //
        // refresh              without parent item         with parent item
        // RefreshSelf          1 level                     ---
        // RefreshParent        2 levels                    1 level
        // RefreshGrandParent   3 levels                    2 levels
        val nextWindow = action.nextWindow ?: item.nextWindow
        val actualNextWindow = when {
            nextWindow == SlimBrowseItemList.NextWindow.Parent && parentItem != null ->
                null
            nextWindow == SlimBrowseItemList.NextWindow.GrandParent && parentItem != null ->
                SlimBrowseItemList.NextWindow.Parent
            nextWindow == SlimBrowseItemList.NextWindow.ParentWithRefresh ->
                if (parentItem != null) null else SlimBrowseItemList.NextWindow.Parent
            else -> nextWindow
        }
        val refreshLevelsFromNextWindow = when {
            nextWindow == SlimBrowseItemList.NextWindow.RefreshSelf && parentItem == null ->
                1
            nextWindow == SlimBrowseItemList.NextWindow.ParentWithRefresh ->
                if (parentItem != null) 1 else 2
            else -> 0
        }
        val refreshLevelsFromRefresh = when (item.actions?.onClickRefresh) {
            JiveActions.RefreshBehavior.RefreshSelf ->
                if (parentItem != null) 0 else 1
            JiveActions.RefreshBehavior.RefreshParent ->
                if (parentItem != null) 1 else 2
            JiveActions.RefreshBehavior.RefreshGrandParent ->
                if (parentItem != null) 2 else 3
            null ->
                // fall back to refreshing the page for do actions without next window, since
                // choices and checkboxes usually do not have that
                if (isGoAction) 0 else 1
        }
        val refreshLevels = max(refreshLevelsFromNextWindow, refreshLevelsFromRefresh)

        return if (isGoAction && nextWindow == null) {
            handleGoAction(item.title, item, parentItem, action)
        } else {
            lifecycleScope.launch {
                connectionHelper.executeAction(playerId, action)
                when (actualNextWindow) {
                    SlimBrowseItemList.NextWindow.Parent -> popLevels(1)
                    SlimBrowseItemList.NextWindow.GrandParent -> popLevels(2)
                    SlimBrowseItemList.NextWindow.Home,
                    SlimBrowseItemList.NextWindow.MyMusic -> goToHome()
                    SlimBrowseItemList.NextWindow.NowPlaying -> {
                        goToHome()
                        listener.openNowPlayingIfNeeded()
                    }
                    else -> {}
                }
                handleMultiLevelRefresh(refreshLevels)
            }
        }
    }

    override fun onOpenSubItemList(
        item: SlimBrowseItemList.SlimBrowseItem,
        itemFetchAction: JiveAction
    ) {
        val f = SlimBrowseSubItemListFragment.create(
            playerId,
            item.title,
            itemFetchAction,
            item.listPosition
        )
        replaceContent(f, "pos${item.listPosition}-subitems", ReplacementMode.OnTopOfStack)
    }

    override fun onOpenWebLink(title: String, link: Uri) {
        val intent = CustomTabsIntent.Builder()
            .build()
        intent.launchUrl(requireContext(), link)
    }

    // SliderBottomSheetFragment.ChangeListener implementation

    override fun onSliderChanged(input: JiveAction): Job = lifecycleScope.launch {
        connectionHelper.executeAction(playerId, input)
    }

    // Lifecycle methods

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.apply {
            addOnBackStackChangedListener(this@MainContentContainerFragment)
            registerFragmentLifecycleCallbacks(
                object : FragmentLifecycleCallbacks() {
                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        super.onFragmentStarted(fm, f)
                        if (f is MainContentChild) {
                            listener.onScrollTargetChanged(f.scrollingTargetView)
                            binding.root.background.alpha = 255
                        }
                    }
                },
                false
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionHelper.playerState(playerId)
                    .flatMapLatest { it.homeMenu }
                    .collectLatest { items ->
                        homeMenu = items
                        if (childFragmentManager.fragments.isEmpty()) {
                            onNodeSelected("home")
                        }
                    }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateBreadcrumbsSubscription()
    }

    override fun onBindingCreated(binding: FragmentMainlistcontainerBinding) {
        // background is only meant to be visible during child fragment transitions,
        // so suppress it until a child fragment is loaded
        binding.root.background.alpha = 0
    }

    // FragmentManager.OnBackStackChangedListener implementation

    override fun onBackStackChanged() {
        if (pendingBack == null) {
            updateBreadcrumbsSubscription()
        }
    }

    override fun onBackStackChangeStarted(fragment: Fragment, pop: Boolean) {
        super.onBackStackChangeStarted(fragment, pop)
        if (pop && fragment.isAdded) {
            pendingBack = PendingBackInfo(fragment, fragment.tag)
            handleStackUpdate()
        }
    }

    override fun onBackStackChangeProgressed(backEvent: BackEventCompat) {
        super.onBackStackChangeProgressed(backEvent)
        pendingBack?.let {
            it.progress = backEvent.progress
            handleStackUpdate()
        }
    }

    override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) {
        super.onBackStackChangeCommitted(fragment, pop)
        if (fragment == pendingBack?.fragment) {
            pendingBack = null
            updateBreadcrumbsSubscription()
            handleStackUpdate()
        }
    }

    override fun onBackStackChangeCancelled() {
        super.onBackStackChangeCancelled()
        pendingBack = null
        updateBreadcrumbsSubscription()
        handleStackUpdate()
    }

    // Internal implementation details

    private fun handleGoAction(
        title: String,
        item: SlimBrowseItemList.SlimBrowseItem?,
        parentItem: SlimBrowseItemList.SlimBrowseItem?,
        action: JiveAction
    ): Job? {
        pendingNavigation?.cancel()
        pendingNavigation = lifecycleScope.launch {
            if (action.isSlideshow) {
                val items = connectionHelper.fetchSlideshowImages(playerId, action)
                val f = GalleryFragment.create(items, title, parentItem?.title)
                replaceContent(f, "gallery", ReplacementMode.OnTopOfStack)
                return@launch
            }
            // Fetch first item of page to check whether we're dealing with a slider or a
            // normal page. Caveat here is that some actions (most notably artist info) cache
            // responses, so we need to make sure to fetch all entries for those as otherwise
            // the paged response might be incomplete later.
            val page = if (action.serverMightCacheResults()) {
                PagingParams.All
            } else {
                PagingParams(0, 1)
            }
            val result = connectionHelper.fetchItemsForAction(playerId, action, page, false)
            val firstItem = result.items.getOrNull(0)
            when {
                result.totalCount == 1 && firstItem?.actions?.slider != null -> {
                    val f = SliderBottomSheetFragment.create(title, firstItem.actions.slider)
                    f.show(childFragmentManager, "slider")
                }
                result.totalCount == 0 && result.window?.textArea != null -> {
                    val f = InfoBottomSheet.create(
                        result.title ?: title,
                        result.window.textArea
                    )
                    f.show(childFragmentManager, "info")
                }
                else -> {
                    val f = SlimBrowseItemListFragment.create(
                        playerId,
                        title,
                        parentItem?.title,
                        parentItem ?: item,
                        action,
                        result.window?.windowStyle
                    )
                    replaceContent(f, "items", ReplacementMode.OnTopOfStack)
                }
            }
        }
        return pendingNavigation
    }

    private fun <T> replaceContent(
        content: T,
        tag: String,
        mode: ReplacementMode
    ) where T : Fragment, T : MainContentChild {
        if (mode != ReplacementMode.OnTopOfStack) {
            goToHome()
        }
        val actualTag = if (mode != ReplacementMode.SetAsHome) {
            val entries = childFragmentManager.run { fragments.size + backStackEntryCount }
            "$tag-level$entries"
        } else {
            tag
        }
        childFragmentManager.commit {
            if (mode != ReplacementMode.SetAsHome) {
                setCustomAnimations(
                    R.animator.main_content_enter,
                    R.animator.main_content_exit,
                    R.animator.main_content_pop_enter,
                    R.animator.main_content_pop_exit
                )
            }
            replace(binding.listContainer.id, content, actualTag)
            if (mode != ReplacementMode.SetAsHome) {
                addToBackStack(actualTag)
            }
        }
    }

    private fun popLevels(levels: Int) = childFragmentManager.apply {
        (0 until min(levels, backStackEntryCount)).forEach { _ -> popBackStackImmediate() }
    }

    private fun handleMultiLevelRefresh(levels: Int) = childFragmentManager.run {
        val start = max(0, backStackEntryCount - levels)
        (start until backStackEntryCount)
            .map { getBackStackEntryAt(it) }
            .mapNotNull { findFragmentByTag(it.name) as? BasePagingListFragment<*, *> }
            .forEach { it.refresh() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updateBreadcrumbsSubscription() {
        val context = requireContext()
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.breadcrumbs_icon_size)

        val tagToTitleAndIconFlows = childFragmentManager.run {
            (0 until backStackEntryCount)
                .map { index -> getBackStackEntryAt(index) }
                .distinctBy { it.name }
                .mapNotNull { entry ->
                    val tag = entry.name ?: throw IllegalStateException()
                    val f = findFragmentByTag(tag) as? MainContentChild
                        ?: return@mapNotNull null
                    val iconFlow = f.iconFlow.flatMapLatest { icon ->
                        flow {
                            // Make sure displaying text isn't stalled by loading icons by emitting
                            // an intermediate null value if network access is needed for loading
                            if (context.imageCacheContains(icon) != true) {
                                emit(null)
                            }
                            emit(context.loadArtwork(icon, iconSize))
                        }
                    }
                    f.titleFlow.combine(iconFlow) { t, i -> tag to PageTitleInfo(t, i) }
                }
        }

        titleSubscription?.cancel()

        if (tagToTitleAndIconFlows.isEmpty()) {
            contentTitles = emptyMap()
            handleStackUpdate()
        } else {
            val tagsToTitlesAndIconsFlow = combine(tagToTitleAndIconFlows) { it.toList() }
            titleSubscription = lifecycleScope.launch {
                tagsToTitlesAndIconsFlow.collect { tagsAndTitles ->
                    contentTitles = tagsAndTitles.associate { it.first to it.second }
                    handleStackUpdate()
                }
            }
        }
    }

    private fun handleStackUpdate() {
        val titles = childFragmentManager.run {
            (0 until backStackEntryCount)
                .map { index -> getBackStackEntryAt(index) }
                .distinctBy { it.name }
                .filter { it.name != pendingBack?.tag }
                .mapNotNull { contentTitles[it.name] }
        }
        val pendingTitle = pendingBack?.tag?.let { contentTitles[it] }
        val progress = pendingBack?.progress ?: 0F
        listener.onContentStackChanged(titles, pendingTitle, progress)
    }

    data class PageTitleInfo(val title: List<String>, val icon: Drawable?)

    companion object {
        fun create(playerId: PlayerId) = MainContentContainerFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
