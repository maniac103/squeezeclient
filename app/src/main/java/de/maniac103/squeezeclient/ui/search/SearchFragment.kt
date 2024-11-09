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

package de.maniac103.squeezeclient.ui.search

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.request.LibrarySearchRequest
import de.maniac103.squeezeclient.databinding.FragmentSearchBinding
import de.maniac103.squeezeclient.databinding.ListItemSearchCategoryBinding
import de.maniac103.squeezeclient.extfuncs.animateScale
import de.maniac103.squeezeclient.extfuncs.backProgressInterpolator
import de.maniac103.squeezeclient.extfuncs.connectionHelper
import de.maniac103.squeezeclient.extfuncs.getParcelable
import de.maniac103.squeezeclient.extfuncs.requireParentAs
import de.maniac103.squeezeclient.model.PagingParams
import de.maniac103.squeezeclient.model.PlayerId
import de.maniac103.squeezeclient.ui.common.BasePrepopulatedListAdapter
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : ViewBindingFragment<FragmentSearchBinding>(FragmentSearchBinding::inflate) {
    interface Listener {
        fun onCloseSearch()
        fun onOpenLocalSearchPage(searchTerm: String, type: LibrarySearchRequest.Mode)
        fun onOpenRadioSearchPage(searchTerm: String)
    }

    private val playerId get() = requireArguments().getParcelable("playerId", PlayerId::class)
    private val listener get() = requireParentAs<Listener>()

    private var submitJob: Job? = null

    private val artistCategory = Category(
        R.string.search_category_artists,
        LibrarySearchRequest.Mode.Artist()
    )
    private val albumCategory = Category(
        R.string.search_category_albums,
        LibrarySearchRequest.Mode.Albums()
    )
    private val genreCategory = Category(
        R.string.search_category_genres,
        LibrarySearchRequest.Mode.Genres()
    )
    private val trackCategory = Category(
        R.string.search_category_tracks,
        LibrarySearchRequest.Mode.Tracks()
    )
    private val radioCategory = Category(
        R.string.search_category_radio,
        null
    )

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            listener.onCloseSearch()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            val progress = 1F - 0.3F * backProgressInterpolator.getInterpolation(backEvent.progress)
            binding.searchPill.apply {
                scaleX = progress
                scaleY = progress
            }
            binding.root.background.alpha = (255F * progress).toInt()
        }

        override fun handleOnBackCancelled() {
            binding.searchPill.animateScale(1F, 200.milliseconds)
            binding.root.background.alpha = 255
        }
    }

    override fun onBindingCreated(binding: FragmentSearchBinding) {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )

        binding.categories.apply {
            val categories = listOf(
                artistCategory,
                albumCategory,
                trackCategory,
                genreCategory,
                radioCategory
            )
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            adapter = Adapter(categories).apply {
                itemSelectionListener = BasePrepopulatedListAdapter.ItemSelectionListener { cat ->
                    val query = binding.editor.text?.toString()
                    if (query != null && cat.count != null) {
                        if (cat.type != null) {
                            listener.onOpenLocalSearchPage(query, cat.type)
                        } else {
                            listener.onOpenRadioSearchPage(query)
                        }
                    }
                    null
                }
            }
        }
        binding.editor.apply {
            doAfterTextChanged { text ->
                submitJob?.cancel()
                if (text.isNullOrEmpty()) {
                    binding.clearButton.isVisible = false
                    binding.categories.isVisible = false
                    binding.divider.isVisible = false
                } else {
                    binding.clearButton.isVisible = true
                    submitJob = lifecycleScope.launch {
                        delay(1.seconds)
                        submitSearch(text.toString())
                    }
                }
            }
            setOnKeyListener { _, keyCode, event ->
                val text = text?.toString()
                if (!text.isNullOrEmpty() &&
                    event.hasNoModifiers() &&
                    event.action == KeyEvent.ACTION_UP &&
                    keyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    submitSearch(text)
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    post {
                        val imm = requireContext().getSystemService(InputMethodManager::class.java)
                        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
        binding.backButton.setOnClickListener {
            listener.onCloseSearch()
        }
        binding.clearButton.setOnClickListener {
            binding.editor.text.clear()
        }
        binding.root.setOnClickListener {
            listener.onCloseSearch()
        }
    }

    private fun submitSearch(query: String) {
        listOf(artistCategory, albumCategory, trackCategory, radioCategory)
            .forEach { it.busy = true }
        binding.divider.isVisible = true
        binding.categories.isVisible = true
        updateAdapter()
        lifecycleScope.launch {
            val results = connectionHelper.getLocalLibrarySearchResultCounts(query)
            artistCategory.count = results.artists
            albumCategory.count = results.albums
            genreCategory.count = results.genres
            trackCategory.count = results.tracks
            updateAdapter()
        }
        lifecycleScope.launch {
            val results = connectionHelper.getRadioSearchResults(
                playerId,
                query,
                PagingParams.CountOnly
            )
            radioCategory.count = results.totalCount
            updateAdapter()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateAdapter() = binding.categories.adapter?.notifyDataSetChanged()

    private class Adapter(items: List<Category>) :
        BasePrepopulatedListAdapter<Category, CategoryViewHolder>(items) {
        override fun onCreateViewHolder(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): CategoryViewHolder {
            val binding = ListItemSearchCategoryBinding.inflate(inflater, parent, false)
            return CategoryViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CategoryViewHolder, item: Category) {
            holder.binding.title.text = holder.binding.root.context.getString(item.titleResId)
            holder.binding.resultCount.isVisible = item.count != null
            holder.binding.resultCount.text = item.count.toString()
            holder.binding.progress.isVisible = item.busy
        }
    }

    private class CategoryViewHolder(val binding: ListItemSearchCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    private data class Category(val titleResId: Int, val type: LibrarySearchRequest.Mode?) {
        var count: Int? = null
            set(value) {
                busy = false
                field = value
            }
        var busy = false
            set(value) {
                if (value) count = null
                field = value
            }
    }

    companion object {
        fun create(playerId: PlayerId) = SearchFragment().apply {
            arguments = bundleOf("playerId" to playerId)
        }
    }
}
