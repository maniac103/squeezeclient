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

package de.maniac103.squeezeclient.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class JiveAction(
    val cmd: List<String>,
    val params: @RawValue Map<String, String?>,
    val nextWindow: SlimBrowseItemList.NextWindow?,
    private val windowIsContextMenu: Boolean?
) : Parcelable {
    val isContextMenu get() = params.containsKey("isContextMenu") || windowIsContextMenu == true
    val isSlideshow get() = params.containsKey("slideshow")

    val serverMightCacheResults get() = cmd.getOrNull(0) == "artistinfo"

    fun withAdditionalParams(params: Map<String, String?>): JiveAction {
        val mergedParams = this.params.toMutableMap().apply { putAll(params) }
        return JiveAction(cmd, mergedParams, nextWindow, windowIsContextMenu)
    }

    fun withInputValue(input: String): JiveAction {
        val newParams = params.entries.associate { (k, v) ->
            when {
                v == "__INPUT__" -> input to null
                v == "__TAGGEDINPUT__" -> k to input
                k == "valtag" && v != null -> v to input
                else -> k to v
            }
        }
        return JiveAction(cmd, newParams, nextWindow, windowIsContextMenu)
    }

    companion object {
        fun createEmptyForInput() = JiveAction(emptyList(), mapOf("" to "__INPUT__"), null, null)
    }
}
