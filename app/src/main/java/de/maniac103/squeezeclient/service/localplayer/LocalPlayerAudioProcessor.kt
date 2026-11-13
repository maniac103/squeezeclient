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
class LocalPlayerAudioProcessor(private val endOfStreamCallback: () -> Unit = {}) :
    BaseAudioProcessor() {
    var hasProcessedData = false
        private set
    var skippedFrames = 0L
        private set
    private var pendingSkipTimeUs = 0L
    private var remainingFramesToSkip = 0
    private var bytesPerFrame = 0
    private var sampleRate = 0

    fun skipAhead(duration: Duration) = synchronized(this) {
        pendingSkipTimeUs = duration.inWholeMicroseconds
        convertSkipTimeIfPossible()
    }

    fun cancelSkip() = synchronized(this) {
        pendingSkipTimeUs = 0L
        remainingFramesToSkip = 0
    }

    override fun onConfigure(audioFormat: AudioProcessor.AudioFormat) = when {
        audioFormat.sampleRate == Format.NO_VALUE -> AudioProcessor.AudioFormat.NOT_SET
        else -> audioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)
        synchronized(this) {
            bytesPerFrame = inputAudioFormat.bytesPerFrame
            skippedFrames = 0L
            sampleRate = inputAudioFormat.sampleRate
            convertSkipTimeIfPossible()
        }
    }

    override fun onReset() {
        super.onReset()
        synchronized(this) {
            hasProcessedData = false
            bytesPerFrame = 0
            sampleRate = 0
        }
    }

    override fun onQueueEndOfStream() {
        super.onQueueEndOfStream()
        endOfStreamCallback()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        synchronized(this) {
            if (inputBuffer.hasRemaining()) {
                hasProcessedData = true
            }
            if (remainingFramesToSkip > 0 && bytesPerFrame > 0) {
                val framesInBuffer = inputBuffer.remaining() / bytesPerFrame
                val skipFrames = min(framesInBuffer, remainingFramesToSkip)
                val skipBytes = skipFrames * bytesPerFrame
                inputBuffer.position(inputBuffer.position() + skipBytes)
                skippedFrames += skipFrames
                remainingFramesToSkip -= skipFrames
            }
        }
        if (inputBuffer.hasRemaining() && !hasPendingOutput()) {
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        }
    }

    private fun convertSkipTimeIfPossible() {
        if (sampleRate > 0) {
            remainingFramesToSkip = (pendingSkipTimeUs * sampleRate / 1_000_000L).toInt()
            pendingSkipTimeUs = 0L
        }
    }
}
