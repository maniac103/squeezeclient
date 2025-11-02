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

import android.content.Context
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

// A copy of the media3 DefaultAudioTrackProvider class that allows accessing the provided track
@OptIn(UnstableApi::class)
class LocalPlayerAudioTrackProvider : DefaultAudioSink.AudioTrackProvider {
    var latestAudioTrack: AudioTrack? = null
        private set

    override fun getAudioTrack(
        audioTrackConfig: AudioSink.AudioTrackConfig,
        audioAttributes: AudioAttributes,
        audioSessionId: Int,
        context: Context?
    ): AudioTrack {
        val audioFormat = Util.getAudioFormat(
            audioTrackConfig.sampleRate,
            audioTrackConfig.channelConfig,
            audioTrackConfig.encoding
        )
        val audioTrackAttributes = if (audioTrackConfig.tunneling) {
            android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()
        } else {
            audioAttributes.getAudioAttributesV21().audioAttributes
        }
        val audioTrackBuilder = AudioTrack.Builder()
            .setAudioAttributes(audioTrackAttributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(audioTrackConfig.bufferSize)
            .setSessionId(audioSessionId)
        if (Build.VERSION.SDK_INT >= 29) {
            setOffloadedPlaybackV29(audioTrackBuilder, audioTrackConfig.offload)
        }
        if (Build.VERSION.SDK_INT >= 34 && context != null) {
            audioTrackBuilder.setContext(context)
        }
        val track = audioTrackBuilder.build()
        latestAudioTrack = track
        return track
    }

    @RequiresApi(29)
    private fun setOffloadedPlaybackV29(
        audioTrackBuilder: AudioTrack.Builder,
        isOffloaded: Boolean
    ) {
        audioTrackBuilder.setOffloadedPlayback(isOffloaded)
    }
}
