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

package de.maniac103.squeezeclient.ui.prefs

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.markodevcic.peko.PermissionRequester
import com.markodevcic.peko.PermissionResult
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.DownloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.LocalPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.await
import de.maniac103.squeezeclient.extfuncs.downloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.fadeInDuration
import de.maniac103.squeezeclient.extfuncs.localPlayerVolumeMode
import de.maniac103.squeezeclient.extfuncs.prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: SharedPreferences
    private lateinit var fadeInPreference: SeekBarPreference
    private lateinit var downloadFolderStructure: ListPreference
    private lateinit var localPlayerEnabled: SwitchPreferenceCompat
    private lateinit var localPlayerVolumeMode: ListPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        prefs = requireContext().prefs

        fadeInPreference = findPreference("fade_in_duration")!!
        fadeInPreference.setOnPreferenceChangeListener { _, newValue ->
            updateFadeInPrefSummary(newValue as Int)
            true
        }
        updateFadeInPrefSummary(prefs.fadeInDuration.inWholeSeconds.toInt())

        downloadFolderStructure = findPreference("download_path_structure")!!
        downloadFolderStructure.entryValues =
            DownloadFolderStructure.entries.map { it.prefValue }.toTypedArray()
        downloadFolderStructure.value = prefs.downloadFolderStructure.prefValue

        localPlayerEnabled = findPreference("local_player_enabled")!!
        localPlayerEnabled.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            val requester = PermissionRequester.instance()
            when {
                !enabled ->
                    true

                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                    // Don't need runtime permission in that case
                    true

                requester.isAnyGranted(android.Manifest.permission.POST_NOTIFICATIONS) ->
                    // Permission already granted
                    true

                else -> {
                    lifecycleScope.launch {
                        if (requestNotificationPermissionForLocalPlayer()) {
                            localPlayerEnabled.isChecked = true
                        }
                    }
                    false
                }
            }
        }

        localPlayerVolumeMode = findPreference("local_player_volume_mode")!!
        localPlayerVolumeMode.setOnPreferenceChangeListener { _, newValue ->
            updateLocalPlayerVolumeModeSummary(newValue as String)
            true
        }
        localPlayerVolumeMode.value = prefs.localPlayerVolumeMode.prefValue
        updateLocalPlayerVolumeModeSummary(localPlayerVolumeMode.value)
    }

    @Suppress("deprecation") // setTargetFragment is deprecated, but needed by super class
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference) {
            val fragment = MaterialListPreferenceDialog.create(preference)
            fragment.setTargetFragment(this, 0)
            fragment.show(parentFragmentManager, "list_preference_dialog")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun updateFadeInPrefSummary(value: Int) {
        fadeInPreference.summary = if (value == 0) {
            getString(R.string.settings_fade_in_seconds_summary_disabled)
        } else {
            resources.getQuantityString(R.plurals.settings_fade_in_seconds_summary, value, value)
        }
    }

    private fun updateLocalPlayerVolumeModeSummary(value: String) {
        val index = LocalPlayerVolumeMode.entries.indexOfFirst { it.prefValue == value }
        val summaries = resources.getStringArray(
            R.array.settings_local_player_volume_mode_summaries
        )
        localPlayerVolumeMode.summary = if (index < 0) null else summaries[index]
    }

    @SuppressLint("InlinedApi")
    private suspend fun requestNotificationPermissionForLocalPlayer(): Boolean {
        val requester = PermissionRequester.instance()
        val result = requester.request(android.Manifest.permission.POST_NOTIFICATIONS).first()
        when (result) {
            is PermissionResult.Denied.NeedsRationale -> {}
            is PermissionResult.Denied.DeniedPermanently -> {}
            is PermissionResult.Cancelled -> return false
            is PermissionResult.Granted -> return true
        }
        // When we're here, we need to show a rationale dialog
        val dialogResult = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.permission_rationale_title)
            .setMessage(R.string.local_player_notification_permission_rationale_message)
            .create()
            .await(
                positiveText = getString(R.string.permission_rationale_allow),
                negativeText = getString(
                    R.string.local_player_notification_permission_rationale_cancel
                )
            )
        if (!dialogResult) {
            return false
        }
        if (result is PermissionResult.Denied.DeniedPermanently) {
            requireActivity().startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
            )
            return false
        }
        return requester.request(android.Manifest.permission.POST_NOTIFICATIONS)
            .first() is PermissionResult.Granted
    }
}
