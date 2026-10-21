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

package de.maniac103.squeezeclient.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.cometd.ConnectionHelper
import de.maniac103.squeezeclient.extfuncs.requestPermission
import de.maniac103.squeezeclient.model.DownloadRequestData
import de.maniac103.squeezeclient.model.PlayerStatus
import de.maniac103.squeezeclient.service.DownloadWorker
import de.maniac103.squeezeclient.service.localplayer.LocalPlaybackService
import de.maniac103.squeezeclient.service.mediasession.MediaService
import de.maniac103.squeezeclient.ui.composables.DialogHost
import de.maniac103.squeezeclient.ui.maincontent.MainActivityState
import de.maniac103.squeezeclient.ui.maincontent.MainActivityUi
import de.maniac103.squeezeclient.ui.maincontent.MainActivityViewModel
import de.maniac103.squeezeclient.ui.maincontent.SelectedPlayerActiveState
import de.maniac103.squeezeclient.ui.playermanagement.PlayerManagementActivity
import de.maniac103.squeezeclient.ui.prefs.SettingsActivity
import de.maniac103.squeezeclient.ui.serversetup.ServerSetupActivity
import de.maniac103.squeezeclient.ui.slideshow.ImageViewActivity
import de.maniac103.squeezeclient.ui.theme.AppTheme
import kotlin.getValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val preferences: Preferences by inject()
    private val volumeChangeRequestFlow = MutableSharedFlow<Int>()
    private val dialogHost = DialogHost()
    private val viewModel by viewModel<MainActivityViewModel>()

    private var selectedPlayerActiveState: SelectedPlayerActiveState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (preferences.serverConfig.value == null) {
            val intent = ServerSetupActivity.createIntent(this, false)
            startActivity(intent)
            finish()
            return
        }

        setContent {
            AppTheme(preferences.appTheme) {
                MainActivityUi(
                    volumeChangeRequestFlow = volumeChangeRequestFlow,
                    onGalleryItemSelected = { image ->
                        val intent = ImageViewActivity.createIntent(this, image)
                        startActivity(intent)
                    },
                    onDownloadSelected = { download ->
                        triggerDownload(download)
                    },
                    onWebLinkSelected = { _, link ->
                        val intent = CustomTabsIntent.Builder().build()
                        intent.launchUrl(this, link)
                    },
                    onOpenServerSetup = {
                        val intent = ServerSetupActivity.createIntent(this, true)
                        startActivity(intent)
                    },
                    onManagePlayers = {
                        startActivity(Intent(this, PlayerManagementActivity::class.java))
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
                dialogHost.Render()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedPlayerActiveStateFlow.collect { state ->
                    selectedPlayerActiveState = state
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalPlaybackService.triggerStartOrStop(this)
    }

    override fun onStop() {
        val selectedPlayerActive = selectedPlayerActiveState
        if (selectedPlayerActive?.isPlaying == true && !isChangingConfigurations) {
            MediaService.start(this, selectedPlayerActive.playerId, true)
        }
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val useVolumeKeys = preferences.useVolumeButtonsForPlayerVolume.value
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP if useVolumeKeys -> {
                adjustVolume(1)
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN if useVolumeKeys -> {
                adjustVolume(-1)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun adjustVolume(direction: Int) = lifecycleScope.launch {
        val stepSize = preferences.volumeStepSize.value
        volumeChangeRequestFlow.emit(direction * stepSize)
    }

    private fun triggerDownload(data: DownloadRequestData) = lifecycleScope.launch {
        if (!requestNotificationPermissionForDownload()) {
            return@launch
        }
        val connectionHelper: ConnectionHelper = get()
        val items = connectionHelper.fetchSongInfosForDownload(data)
        DownloadWorker.enqueue(items)
    }

    private suspend fun requestNotificationPermissionForDownload(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Don't need runtime permission in that case
            return true
        }

        return requestPermission(
            dialogHost = dialogHost,
            permission = Manifest.permission.POST_NOTIFICATIONS,
            rationaleDialogTextResId = R.string.download_permission_rationale_message,
            rationaleDialogCancelButtonTextResId = R.string.download_permission_rationale_cancel
        )
    }
}
