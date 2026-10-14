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
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.markodevcic.peko.PermissionRequester
import de.maniac103.squeezeclient.service.DownloadWorker
import de.maniac103.squeezeclient.service.localplayer.LocalPlaybackService
import de.maniac103.squeezeclient.ui.viewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class SqueezeClientApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this)
        super.onCreate()
        PermissionRequester.initialize(this)

        startKoin {
            androidLogger()
            androidContext(this@SqueezeClientApplication)
            workManagerFactory()
            modules(globalModule, viewModelModule)
        }

        val preferences: Preferences by inject()

        applicationScope.launch {
            preferences.appTheme.asFlow().collect { themeSetting ->
                updateDefaultNightMode(themeSetting)
            }
        }
        applicationScope.launch {
            preferences.localPlayerEnabled.asFlow().collect {
                LocalPlaybackService.triggerStartOrStop(this@SqueezeClientApplication)
            }
        }

        DownloadWorker.startObservingStatus(this)
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    private fun updateDefaultNightMode(setting: Preferences.AppThemeMode) {
        val mode = when (setting) {
            Preferences.AppThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO

            Preferences.AppThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES

            Preferences.AppThemeMode.SystemDefault -> when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY

                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
