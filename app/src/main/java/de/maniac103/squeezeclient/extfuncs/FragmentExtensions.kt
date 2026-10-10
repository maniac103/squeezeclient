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

package de.maniac103.squeezeclient.extfuncs

import androidx.fragment.app.Fragment
import de.maniac103.squeezeclient.Preferences

val Fragment.connectionHelper get() = requireContext().connectionHelper
val Fragment.prefs get() = requireContext().prefs
val Fragment.preferences get() = Preferences(prefs)

inline fun <reified T> Fragment.parentAs() = parentFragment as? T ?: activity as? T
inline fun <reified T> Fragment.requireParentAs() = parentAs<T>() ?: throw IllegalStateException(
    "Parent of fragment $this doesn't implement required interface ${T::class.java.simpleName}"
)
