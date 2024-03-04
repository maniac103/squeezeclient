/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation,
 *   either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.maniac103.squeezeclient.ui.contextmenu

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.maniac103.squeezeclient.databinding.BottomSheetContextMenuBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.getParcelableList
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ContextMenuBottomSheetFragment :
    BottomSheetDialogFragment(),
    ContextMenuItemListFragment.ItemClickListener {
    interface Listener {
        fun onContextItemSelected(
            parentTitle: String,
            item: SlimBrowseItemList.SlimBrowseItem
        ): Job?
    }

    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val parent get() =
        requireArguments().getParcelable("parent", SlimBrowseItemList.SlimBrowseItem::class)
    private val initialItems get() = requireArguments().getParcelableList(
        "initialItems",
        SlimBrowseItemList.SlimBrowseItem::class
    )

    private lateinit var binding: BottomSheetContextMenuBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetContextMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.layoutTransition = LayoutTransition().apply {
            setDuration(200)
            setAnimateParentHierarchy(false)
        }

        val parent = parent
        binding.icon.apply {
            val iconUrl = parent.extractIconUrl(requireContext())
            isVisible = iconUrl != null
            load(iconUrl)
        }
        binding.title.text = parent.title
        binding.subtext.apply {
            isVisible = !parent.subText.isNullOrEmpty()
            text = parent.subText
        }
        binding.breadcrumbBackButton.setOnClickListener {
            childFragmentManager.popBackStack()
        }

        childFragmentManager.apply {
            addOnBackStackChangedListener {
                binding.breadcrumbContainer.isVisible = backStackEntryCount > 0
                val f = findFragmentById(binding.listContainer.id) as? ContextMenuItemListFragment
                binding.breadcrumbText.text = f?.parent?.title
            }
            commit {
                replace(
                    binding.listContainer.id,
                    ContextMenuItemListFragment.create(parent, initialItems)
                )
            }
        }
    }

    override fun onItemClicked(item: SlimBrowseItemList.SlimBrowseItem): Job? {
        val goAction = item.actions?.goAction
        if (goAction?.isContextMenu == true) {
            return lifecycleScope.launch {
                val newItems = connectionHelper.fetchItemsForAction(
                    playerId,
                    goAction,
                    PagingParams.All
                )
                childFragmentManager.commit(true) {
                    replace(
                        binding.listContainer.id,
                        ContextMenuItemListFragment.create(item, newItems.items)
                    )
                    addToBackStack(goAction.toString())
                }
            }
        }

        val listener = (parentFragment as? Listener) ?: (activity as? Listener)
        val job = listener?.onContextItemSelected(parent.title, item)
        job?.invokeOnCompletion {
            if (isAdded) {
                dismissAllowingStateLoss()
            }
        }
        return job
    }

    companion object {
        fun create(
            playerId: PlayerId,
            parent: SlimBrowseItemList.SlimBrowseItem,
            initialItems: List<SlimBrowseItemList.SlimBrowseItem>
        ) = ContextMenuBottomSheetFragment().apply {
            arguments = bundleOf(
                "playerId" to playerId,
                "parent" to parent,
                "initialItems" to ArrayList(initialItems)
            )
        }
    }
}
