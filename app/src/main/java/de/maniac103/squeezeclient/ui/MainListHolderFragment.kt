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

import android.os.Bundle
import android.view.View
import androidx.activity.BackEventCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.databinding.FragmentMainlistcontainerBinding
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.ui.common.BasePagingListFragment
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import java.lang.IllegalStateException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainListHolderFragment :
    ViewBindingFragment<FragmentMainlistcontainerBinding>(
        FragmentMainlistcontainerBinding::inflate
    ),
    FragmentManager.OnBackStackChangedListener {

    interface Listener {
        fun onScrollTargetChanged(scrollTarget: View?)
        fun onContentStackChanged(
            titles: List<String>,
            pendingTitle: String?,
            pendingProgress: Float
        )
    }

    interface Child {
        val scrollingTargetView: View
        val titleFlow: Flow<String?>
    }

    enum class ReplacementMode {
        SetAsHome,
        OnTopOfHome,
        OnTopOfStack
    }

    val hasContent get() = childFragmentManager.fragments.isNotEmpty()
    private val listener get() = requireParentAs<Listener>()

    data class PendingBackInfo(
        val fragment: Fragment,
        val tag: String?,
        var progress: Float = 0F
    )

    private var titleSubscription: Job? = null
    private var contentTitles = emptyMap<String, String?>()
    private var pendingBack: PendingBackInfo? = null

    fun <T> replaceContent(
        content: T,
        tag: String,
        mode: ReplacementMode
    ) where T : Fragment, T : Child {
        if (mode != ReplacementMode.OnTopOfStack) {
            clearBackStack()
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

    fun clearBackStack() = childFragmentManager.apply {
        while (backStackEntryCount > 0) {
            popBackStackImmediate()
        }
    }

    fun handleMultiLevelRefresh(levels: Int) = childFragmentManager.run {
        (backStackEntryCount - levels)
            .takeIf { it >= 0 }
            ?.let { getBackStackEntryAt(it) }
            ?.let { findFragmentByTag(it.name) as? BasePagingListFragment<*, *> }
            ?.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.apply {
            addOnBackStackChangedListener(this@MainListHolderFragment)
            registerFragmentLifecycleCallbacks(
                object : FragmentLifecycleCallbacks() {
                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        super.onFragmentStarted(fm, f)
                        val scrollTarget = (f as? Child)?.scrollingTargetView
                        listener.onScrollTargetChanged(scrollTarget)
                        binding.root.background.alpha = 255
                    }
                },
                false
            )
        }
    }

    override fun onStart() {
        super.onStart()
        handleStackUpdate()
    }

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

    override fun onBindingCreated(binding: FragmentMainlistcontainerBinding) {
        // background is only meant to be visible during child fragment transitions,
        // so suppress it until a child fragment is loaded
        binding.root.background.alpha = 0
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
                .map { index -> getBackStackEntryAt(index) }
                .distinctBy { it.name }
                .filter { it.name != pendingBack?.tag }
                .mapNotNull { contentTitles[it.name] }
        }
        val pendingTitle = pendingBack?.tag?.let { contentTitles[it] }
        val progress = pendingBack?.progress ?: 0F
        listener.onContentStackChanged(titles, pendingTitle, progress)
    }

    companion object {
        fun create() = MainListHolderFragment()
    }
}
