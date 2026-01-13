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

import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.time.Duration

@UnstableApi
class LocalPlayerAudioProcessor(private val resetCallback: () -> Unit = {}) : BaseAudioProcessor() {
    var hasProcessedData = false
        private set
    var skippedFrames = 0L
        private set
    private var remainingFramesToSkip = 0L
    private var bytesPerFrame = 0

    fun skipAhead(duration: Duration) {
        remainingFramesToSkip =
            duration.inWholeMicroseconds * inputAudioFormat.sampleRate / 1000000L
    }

    override fun onConfigure(audioFormat: AudioProcessor.AudioFormat) = when {
        audioFormat.sampleRate == Format.NO_VALUE -> AudioProcessor.AudioFormat.NOT_SET
        else -> audioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)
        skippedFrames = 0L
        remainingFramesToSkip = 0L
        bytesPerFrame = inputAudioFormat.bytesPerFrame
    }

    override fun onReset() {
        hasProcessedData = false
        super.onReset()
        resetCallback()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.hasRemaining()) {
            hasProcessedData = true
        }
        if (remainingFramesToSkip > 0 && bytesPerFrame > 0) {
            val framesInBuffer = (inputBuffer.remaining() / bytesPerFrame).toLong()
            val skipFrames = min(framesInBuffer, remainingFramesToSkip)
            val skipBytes = skipFrames * bytesPerFrame
            inputBuffer.position(inputBuffer.position() + skipBytes.toInt())
            skippedFrames += skipFrames
            remainingFramesToSkip -= skipFrames
        }
        if (inputBuffer.hasRemaining() && !hasPendingOutput()) {
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        }
    }
}
