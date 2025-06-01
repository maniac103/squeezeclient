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

package de.maniac103.squeezeclient

import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.markodevcic.peko.PermissionRequester
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.service.DownloadWorker
import de.maniac103.squeezeclient.service.localplayer.LocalPlayerStartupWorker
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class SqueezeClientApplication :
    Application(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    val json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }
    val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val serverConfig = prefs.serverConfig
            val authHeader = serverConfig?.credentialsAsAuthorizationHeader
            val actualRequest = when {
                request.url.host == serverConfig?.url?.host && authHeader != null ->
                    request.newBuilder().addHeader("Authorization", authHeader).build()

                else -> request
            }
            chain.proceed(actualRequest)
        }
        .followRedirects(true)
        .build()
    val connectionHelper = ConnectionHelper(this)
    val backProgressInterpolator by lazy {
        AnimationUtils.loadInterpolator(this, android.R.interpolator.decelerate_quint)
    }

    override fun onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this)
        super.onCreate()
        PermissionRequester.initialize(this)

        // Don't use a lambda here, it might be garbage collected
        // (internally, listeners are stored in a WeakHashMap)
        prefs.registerOnSharedPreferenceChangeListener(this)
        updateDefaultNightMode()

        DownloadWorker.startObservingStatus(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        when (key) {
            "app_theme" -> updateDefaultNightMode()
            "local_player_enabled" -> LocalPlayerStartupWorker.triggerStartOrStop(this)
        }
    }

    private fun updateDefaultNightMode() {
        val mode = when (prefs.getString("app_theme", null)) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
