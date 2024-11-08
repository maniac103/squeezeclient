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

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.databinding.FragmentGenericListBinding
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.extfuncs.showActionTimePicker
import de.maniac103.squeezeclient.model.JiveAction
import de.maniac103.squeezeclient.model.JiveActions
import de.maniac103.squeezeclient.model.JiveHomeMenuItem
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.MainListHolderFragment
import de.maniac103.squeezeclient.ui.bottomsheets.ChoicesBottomSheetFragment
import de.maniac103.squeezeclient.ui.bottomsheets.InputBottomSheetFragment
import de.maniac103.squeezeclient.ui.common.BasePrepopulatedListAdapter
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class JiveHomeListItemFragment :
    ViewBindingFragment<FragmentGenericListBinding>(FragmentGenericListBinding::inflate),
    MainListHolderFragment.Child,
    BasePrepopulatedListAdapter.ItemSelectionListener<JiveHomeMenuItem>,
    ChoicesBottomSheetFragment.SelectionListener,
    InputBottomSheetFragment.InputSubmitListener {
    interface NavigationListener {
        fun onNodeSelected(nodeId: String)
        fun onGoAction(title: String, action: JiveAction): Job?
    }

    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val nodeId get() = requireArguments().getString("nodeId")!!
    private val listener get() = requireParentAs<NavigationListener>()
    override val scrollingTargetView get() = binding.recycler

    @OptIn(ExperimentalCoroutinesApi::class)
    override val titleFlow get() = connectionHelper
        .playerState(playerId)
        .flatMapLatest { it.homeMenu }
        .map { it[nodeId]?.title }

    private var adapter: JiveHomeItemListAdapter? = null
    private var latestMenu = mapOf<String, JiveHomeMenuItem>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val playerState = connectionHelper.playerState(playerId)
                playerState.flatMapLatest { it.homeMenu }.collect { menu ->
                    latestMenu = menu
                    updateMenuData()
                }
            }
        }
    }

    override fun onBindingCreated(binding: FragmentGenericListBinding) {
        binding.root.enableMainContentBackground()
        binding.recycler.layoutManager = LinearLayoutManager(
            requireContext(),
            RecyclerView.VERTICAL,
            false
        )
        adapter = JiveHomeItemListAdapter().apply {
            itemSelectionListener = this@JiveHomeListItemFragment
            binding.recycler.adapter = this
        }
        updateMenuData()
    }

    override fun onItemSelected(item: JiveHomeMenuItem): Job? {
        when {
            item.input != null -> showInput(item)
            item.choices != null -> showChoices(item)
            item.doAction != null -> return lifecycleScope.launch {
                connectionHelper.executeAction(playerId, item.doAction)
            }
            item.goAction != null -> return listener.onGoAction(item.title, item.goAction)
            else -> listener.onNodeSelected(item.id)
        }
        return null
    }

    override fun onChoiceSelected(choice: JiveAction, refresh: JiveActions.RefreshBehavior?) =
        lifecycleScope.launch {
            connectionHelper.executeAction(playerId, choice)
        }

    override fun onInputSubmitted(title: String, action: JiveAction, isGoAction: Boolean) =
        if (isGoAction) {
            listener.onGoAction(title, action)
        } else {
            lifecycleScope.launch {
                connectionHelper.executeAction(playerId, action)
            }
        }

    private fun showInput(item: JiveHomeMenuItem) {
        val input = requireNotNull(item.input)
        if (input.type == JiveActions.Input.Type.Time) {
            showActionTimePicker(item.title, input) {
                onInputSubmitted(item.title, input.action.withInputValue(it), false)
            }
        } else {
            val f = InputBottomSheetFragment.createForInput(item.title, input)
            f.show(childFragmentManager, "input")
        }
    }

    private fun showChoices(item: JiveHomeMenuItem) {
        val choices = item.choices ?: return
        val f = ChoicesBottomSheetFragment.create(item.title, choices, null)
        f.show(childFragmentManager, "choices")
    }

    private fun updateMenuData() {
        val items = latestMenu.values.filter { it.node == nodeId }.sortedBy { it.sortWeight }
        adapter?.replaceItems(items)
    }

    companion object {
        fun create(playerId: PlayerId, nodeId: String) = JiveHomeListItemFragment().apply {
            arguments = bundleOf("playerId" to playerId, "nodeId" to nodeId)
        }
    }
}
