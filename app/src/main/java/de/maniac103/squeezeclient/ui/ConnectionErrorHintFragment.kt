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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import de.maniac103.squeezeclient.databinding.FragmentConnectionFailHintBinding

class ConnectionErrorHintFragment : Fragment() {
    fun interface Listener {
        fun onActionInvoked(index: Int, tag: String?)
    }

    private lateinit var binding: FragmentConnectionFailHintBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentConnectionFailHintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        binding.image.setImageResource(args.getInt("icon"))
        binding.hintText.text = getString(args.getInt("text"), args.getString("textArgument"))
        binding.actionButton1.bindToAction(0, args.getInt("action1"), args.getString("action1tag"))
        binding.actionButton2.bindToAction(1, args.getInt("action2"), args.getString("action2tag"))
    }

    private fun Button.bindToAction(index: Int, labelResId: Int, tag: String?) {
        val listener = activity as? Listener
        if (labelResId != 0 && listener != null) {
            text = getString(labelResId)
            setOnClickListener { listener.onActionInvoked(index, tag) }
            isVisible = true
        } else {
            isVisible = false
        }
    }

    companion object {
        fun create(
            @DrawableRes iconResId: Int,
            @StringRes textResId: Int,
            textArgument: String? = null,
            @StringRes action1LabelResId: Int? = null,
            @StringRes action2LabelResId: Int? = null,
            action1Tag: String? = null,
            action2Tag: String? = null
        ) = ConnectionErrorHintFragment().apply {
            arguments = bundleOf(
                "icon" to iconResId,
                "text" to textResId,
                "textArgument" to textArgument,
                "action1" to (action1LabelResId ?: 0),
                "action1tag" to action1Tag,
                "action2" to (action2LabelResId ?: 0),
                "action2tag" to action2Tag
            )
        }
    }
}