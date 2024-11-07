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

package de.maniac103.squeezeclient.ui.common

import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.loadArtwork
import de.maniac103.squeezeclient.model.SlimBrowseItemList
import kotlinx.coroutines.Job

class SlimBrowseItemListViewHolder(private val binding: SlimBrowseItemListAdapter.ItemBinding) :
    RecyclerView.ViewHolder(binding.root) {
    val contextMenu: View? = itemView.findViewById(R.id.context_menu)
    val radio: RadioButton? = itemView.findViewById(R.id.radio)
    private val checkbox: CheckBox? = itemView.findViewById(R.id.checkbox)
    private val choiceLabel: TextView? = itemView.findViewById(R.id.choice_label)

    fun bind(item: SlimBrowseItemList.SlimBrowseItem) {
        binding.title.text = item.title
        binding.subText.text = item.subText
        binding.subText.isVisible = !item.subText.isNullOrEmpty()
        if (binding.icon.isVisible) {
            binding.icon.loadArtwork(item)
        }
        item.actions?.radio?.let { radio?.isChecked = it.state }
        item.actions?.checkbox?.let { checkbox?.isChecked = it.state }
        item.actions?.choices?.let { choiceLabel?.text = it.items[it.selectedIndex].title }
    }

    fun setupBusyListener(job: Job) {
        val updateBusyState = { busy: Boolean ->
            binding.contextContainer.forEach { child ->
                val isBusyIndicator = child == binding.loadingIndicator
                child.isVisible = busy == isBusyIndicator
            }
        }
        updateBusyState(true)
        job.invokeOnCompletion { updateBusyState(false) }
    }
}
