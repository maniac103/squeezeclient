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
import de.maniac103.squeezeclient.extfuncs.requireParentAs
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
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeItemListNavigationListener
import de.maniac103.squeezeclient.ui.itemlist.JiveHomeListItemFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListFragment
import de.maniac103.squeezeclient.ui.itemlist.SlimBrowseItemListNavigationListener
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainListHolderFragment :
    ViewBindingFragment<FragmentMainlistcontainerBinding>(
        FragmentMainlistcontainerBinding::inflate
    ),
    JiveHomeItemListNavigationListener,
    SlimBrowseItemListNavigationListener,
    SliderBottomSheetFragment.ChangeListener,
    FragmentManager.OnBackStackChangedListener {

    interface Listener {
        fun onScrollTargetChanged(scrollTarget: View?)
        fun onContentStackChanged(
            titles: List<String>,
            pendingTitle: List<String>?,
            pendingProgress: Float
        )
        fun openNowPlayingIfNeeded()
    }

    interface Child {
        val scrollingTargetView: View
        val titleFlow: Flow<List<String>>
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
    private var contentTitles = emptyMap<String, List<String>>()
    private var pendingBack: PendingBackInfo? = null
    private var pendingNavigation: Job? = null
    private var homeMenu: Map<String, JiveHomeMenuItem> = mapOf()

    // Public methods

    fun handleGoAction(title: String, parentTitle: String?, action: JiveAction): Job? {
        pendingNavigation?.cancel()
        pendingNavigation = lifecycleScope.launch {
            if (action.isSlideshow) {
                val items = connectionHelper.fetchSlideshowImages(playerId, action)
                val f = GalleryFragment.create(items, title, parentTitle)
                replaceContent(f, "gallery", ReplacementMode.OnTopOfStack)
                return@launch
            }
            // Fetch first item of page to check whether we're dealing with a slider or a normal page
            val result = connectionHelper.fetchItemsForAction(playerId, action, PagingParams(0, 1))
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
                        parentTitle,
                        action,
                        result.window?.windowStyle
                    )
                    replaceContent(f, "items", ReplacementMode.OnTopOfStack)
                }
            }
        }
        return pendingNavigation
    }

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
            val mode = if (nodeId == "home") {
                ReplacementMode.SetAsHome
            } else {
                ReplacementMode.OnTopOfStack
            }
            replaceContent(f, "home:$nodeId", mode)
        }
    }

    override fun onGoAction(title: String, action: JiveAction): Job? =
        handleGoAction(title, null, action)

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
            else -> 0
        }
        val refreshLevels = max(refreshLevelsFromNextWindow, refreshLevelsFromRefresh)

        return if (isGoAction && actualNextWindow == null) {
            handleGoAction(item.title, parentItem?.title, action)
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
            addOnBackStackChangedListener(this@MainListHolderFragment)
            registerFragmentLifecycleCallbacks(
                object : FragmentLifecycleCallbacks() {
                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        super.onFragmentStarted(fm, f)
                        if (f is Child) {
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
        handleStackUpdate()
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

    private fun <T> replaceContent(
        content: T,
        tag: String,
        mode: ReplacementMode
    ) where T : Fragment, T : Child {
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

    private fun updateBreadcrumbsSubscription() {
        val tagToTitleFlows = childFragmentManager.run {
            (0 until backStackEntryCount)
                .map { index -> getBackStackEntryAt(index) }
                .distinctBy { it.name }
                .mapNotNull { entry ->
                    val tag = entry.name ?: throw IllegalStateException()
                    val f = findFragmentByTag(tag)
                    (f as? Child)?.titleFlow?.map { tag to it }
                }
        }

        titleSubscription?.cancel()

        if (tagToTitleFlows.isEmpty()) {
            contentTitles = emptyMap()
            handleStackUpdate()
        } else {
            val tagsToTitlesFlow = combine(tagToTitleFlows) { it.toList() }
            titleSubscription = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    tagsToTitlesFlow.collect { tagsAndTitles ->
                        contentTitles = tagsAndTitles.associate { it.first to it.second }
                        handleStackUpdate()
                    }
                }
            }
        }
    }

    private fun handleStackUpdate() {
        val titles = childFragmentManager.run {
            (0 until backStackEntryCount)
                .asSequence()
                .map { index -> getBackStackEntryAt(index) }
                .distinctBy { it.name }
                .filter { it.name != pendingBack?.tag }
                .mapNotNull { contentTitles[it.name] }
                .flatten()
                .toList()
        }
        val pendingTitle = pendingBack?.tag?.let { contentTitles[it] }
        val progress = pendingBack?.progress ?: 0F
        listener.onContentStackChanged(titles, pendingTitle, progress)
    }

    companion object {
        fun create(playerId: PlayerId) = MainListHolderFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
