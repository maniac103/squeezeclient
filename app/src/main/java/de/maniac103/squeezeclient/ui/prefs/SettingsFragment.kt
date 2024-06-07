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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.DownloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.downloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.fadeInDuration
import de.maniac103.squeezeclient.extfuncs.prefs

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: SharedPreferences
    private lateinit var fadeInPreference: SeekBarPreference
    private lateinit var downloadFolderStructure: ListPreference

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
}
