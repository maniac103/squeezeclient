/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2026 Danny Baumann
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

package de.maniac103.squeezeclient.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface TextResource {
    @Composable
    fun resolve(): String

    class Raw(val text: String) : TextResource {
        @Composable
        override fun resolve() = text
    }

    class StringRes(val resId: Int, vararg args: Any) : TextResource {
        private val argsArray = args

        @Composable
        override fun resolve() = stringResource(resId, *argsArray)
    }

    class PluralRes(val resId: Int, val quantity: Int, vararg args: Any) : TextResource {
        private val argsArray = args

        @Composable
        override fun resolve() = pluralStringResource(resId, quantity, *argsArray)
    }
}

fun String.toTextResource() = TextResource.Raw(this)
