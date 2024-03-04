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

package de.maniac103.squeezeclient.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
data class JiveActions(
    val goAction: JiveAction?,
    val doAction: JiveAction?,
    val moreAction: JiveAction?,
    val playAction: JiveAction?,
    val addAction: JiveAction?,
    val insertAction: JiveAction?,
    val downloadData: DownloadRequestData?,
    val choices: Choices?,
    val checkbox: Checkbox?,
    val radio: Radio?,
    val slider: Slider?,
    val input: Input?
) : Parcelable {
    val hasContextMenu get() = playAction != null ||
        addAction != null ||
        insertAction != null ||
        moreAction != null ||
        downloadData != null

    @Parcelize
    data class Choices(
        val items: List<Choice>,
        val selectedIndex: Int
    ) : Parcelable

    @Parcelize
    data class Choice(
        val title: String,
        val action: JiveAction
    ) : Parcelable

    @Parcelize
    data class Slider(
        val min: Int,
        val max: Int,
        val initialValue: Int,
        val icons: String,
        val action: JiveAction
    ) : Parcelable

    @Parcelize
    data class Checkbox(
        val state: Boolean,
        val onAction: JiveAction,
        val offAction: JiveAction
    ) : Parcelable

    @Parcelize
    data class Radio(
        val state: Boolean,
        val action: JiveAction
    ) : Parcelable

    // inputStyle
    @Parcelize
    data class Input(
        val minLength: Int,
        val initialText: String?,
        val allowedChars: String?,
        val type: Type,
        val action: JiveAction,
        val actionHasTarget: Boolean
    ) : Parcelable {
        @Serializable
        enum class Type {
            // Text input
            @SerialName("text")
            Text,

            // Time picker, expects output in seconds
            @SerialName("time")
            Time
        }
    }
}
