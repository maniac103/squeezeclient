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

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import de.maniac103.squeezeclient.SqueezeClientApplication

val Context.connectionHelper get() =
    (applicationContext as SqueezeClientApplication).connectionHelper
val Context.jsonParser get() =
    (applicationContext as SqueezeClientApplication).json
val Context.httpClient get() =
    (applicationContext as SqueezeClientApplication).httpClient
val Context.prefs: SharedPreferences get() =
    PreferenceManager.getDefaultSharedPreferences(this)
