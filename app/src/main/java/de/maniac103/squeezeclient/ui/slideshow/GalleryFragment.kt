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

package de.maniac103.squeezeclient.ui.slideshow

import android.app.Activity
import android.app.ActivityOptions
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.databinding.FragmentGenericListBinding
import de.maniac103.squeezeclient.databinding.GridItemGalleryBinding
import de.maniac103.squeezeclient.extfuncs.getParcelableList
import de.maniac103.squeezeclient.extfuncs.loadSlideshowImage
import de.maniac103.squeezeclient.model.SlideshowImage
import de.maniac103.squeezeclient.ui.MainContentChild
import de.maniac103.squeezeclient.ui.common.BasePrepopulatedListAdapter
import de.maniac103.squeezeclient.ui.common.ViewBindingFragment
import kotlinx.coroutines.flow.flowOf

class GalleryFragment :
    ViewBindingFragment<FragmentGenericListBinding>(FragmentGenericListBinding::inflate),
    MainContentChild {
    private val items get() = requireArguments().getParcelableList("items", SlideshowImage::class)
    override val titleFlow get() = flowOf(requireArguments().getStringArrayList("title")!!)
    override val iconFlow get() = flowOf(null)
    override val scrollingTargetView get() = binding.recycler

    override fun onBindingCreated(binding: FragmentGenericListBinding) {
        binding.root.enableMainContentBackground()
        binding.recycler.apply {
            layoutManager = GridLayoutManager(requireContext(), 2, RecyclerView.VERTICAL, false)
            adapter = GalleryAdapter(items)
        }
    }

    private class GalleryAdapter(items: List<SlideshowImage>) :
        BasePrepopulatedListAdapter<SlideshowImage, GalleryViewHolder>(items) {
        override fun onCreateViewHolder(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ): GalleryViewHolder {
            val binding = GridItemGalleryBinding.inflate(inflater, parent, false)
            return GalleryViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GalleryViewHolder, item: SlideshowImage) {
            val context = holder.binding.root.context
            holder.binding.root.setOnClickListener {
                val intent = ImageViewActivity.createIntent(context, item)
                val options = (context as? Activity)?.let { activity ->
                    ActivityOptions.makeSceneTransitionAnimation(
                        activity,
                        holder.binding.image,
                        "image"
                    )
                }
                context.startActivity(intent, options?.toBundle())
            }

            holder.binding.loadingIndicator.isVisible = true
            holder.binding.image.loadSlideshowImage(item) {
                listener(
                    onSuccess = { request, result ->
                        holder.binding.loadingIndicator.isVisible = false
                    }
                )
            }
            holder.binding.text.text = item.caption
        }
    }

    private class GalleryViewHolder(val binding: GridItemGalleryBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        fun create(items: List<SlideshowImage>, title: String, parentTitle: String?) =
            GalleryFragment().apply {
                val titleList = listOfNotNull(parentTitle, title)
                arguments = bundleOf("items" to ArrayList(items), "title" to ArrayList(titleList))
            }
    }
}
