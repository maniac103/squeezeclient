/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2025 Danny Baumann
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

package de.maniac103.squeezeclient.service.localplayer

import android.media.AudioTrack
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider

@OptIn(UnstableApi::class)
class LocalPlayerAudioOutputProvider(provider: AudioTrackAudioOutputProvider) :
    ForwardingAudioOutputProvider(provider) {
    var latestAudioTrack: AudioTrack? = null
        private set

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val output = super.getAudioOutput(config)
        latestAudioTrack = (output as? AudioTrackAudioOutput)?.audioTrack
        return output
    }
}
