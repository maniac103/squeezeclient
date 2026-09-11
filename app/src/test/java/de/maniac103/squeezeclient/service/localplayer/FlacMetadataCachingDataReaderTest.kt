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

package de.maniac103.squeezeclient.service.localplayer

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.util.UnstableApi
import java.io.ByteArrayOutputStream
import kotlin.math.min
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests resuming a FLAC stream at an arbitrary byte offset, as it happens when the buffered part
 * of a stream ran dry and the loader re-requests the stream (e.g. after resuming a paused sync
 * group). The resumed data starts mid-frame, so the reader has to skip ahead to the next frame
 * sync before replaying the cached metadata - otherwise the extractor sees frame data after the
 * metadata and fails with 'First frame does not start with sync code'.
 */
@OptIn(UnstableApi::class)
class FlacMetadataCachingDataReaderTest {

    private class ByteArrayDataReader(
        private val data: ByteArray,
        position: Int = 0
    ) : DataReader {
        private var readPosition = position

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (readPosition >= data.size) {
                return C.RESULT_END_OF_INPUT
            }
            val bytesToCopy = min(length, data.size - readPosition)
            data.copyInto(buffer, offset, readPosition, readPosition + bytesToCopy)
            readPosition += bytesToCopy
            return bytesToCopy
        }
    }

    private fun readAll(reader: DataReader): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val bytesRead = reader.read(buffer, 0, buffer.size)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                return result.toByteArray()
            }
            result.write(buffer, 0, bytesRead)
        }
    }

    private fun frame(sampleRateAndBlockSize: Int, payloadByte: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xF8.toByte(), sampleRateAndBlockSize.toByte()) +
            ByteArray(10) { payloadByte.toByte() }

    private fun isFrameSync(data: ByteArray, index: Int): Boolean {
        if (index + 3 > data.size) {
            return false
        }
        if (data[index] != 0xFF.toByte()) {
            return false
        }
        if ((data[index + 1].toInt() and 0xFE) != 0xF8) {
            return false
        }
        val third = data[index + 2].toInt() and 0xFF
        return (third and 0xF0) != 0 && (third and 0x0F) <= 0x0B
    }

    /** Builds a minimal FLAC stream: magic, one STREAMINFO block and four frames. */
    private fun flacStream(): ByteArray {
        val streamInfo = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x22) + ByteArray(34) { 0x42 }
        val frames = frame(0x36, 0x11) + frame(0x36, 0x22) + frame(0x36, 0x33) +
            frame(0x36, 0x44)
        return "fLaC".toByteArray() + streamInfo + frames
    }

    @Test
    fun readingFullStreamCachesMetadata() {
        val stream = flacStream()
        val reader = FlacMetadataCachingDataReader(ByteArrayDataReader(stream), FlacMetadataCache())

        assertArrayEquals(stream, readAll(reader))
    }

    @Test
    fun resumedStreamContinuesAtNextFrameSync() {
        val stream = flacStream()
        val cache = FlacMetadataCache()
        readAll(FlacMetadataCachingDataReader(ByteArrayDataReader(stream), cache))
        val metadataSize = cache.metadataBytes!!.size

        // Resume in the middle of the payload of the second frame (magic + block header +
        // STREAMINFO + one frame + 5 bytes of the second frame's payload).
        val resumeOffset = 4 + 4 + 34 + 13 + 5
        val resumeReader = FlacMetadataCachingDataReader(
            ByteArrayDataReader(stream, resumeOffset),
            cache
        )
        val resumed = readAll(resumeReader)

        // The cached metadata is replayed, but the data after it must not contain the mid-frame
        // bytes that followed the resume point; it has to start at the next frame sync.
        assertArrayEquals(
            stream.copyOfRange(0, metadataSize),
            resumed.copyOfRange(0, metadataSize)
        )
        val firstSync = (metadataSize..resumed.size - 3).firstOrNull { isFrameSync(resumed, it) }
        assertEquals(
            "Data after the replayed metadata must start at a frame sync",
            metadataSize,
            firstSync ?: -1
        )

        // Everything from the sync onwards must be unchanged stream data. The skipped part of
        // the frame containing the resume position is (intentionally) dropped, so the emitted
        // stream continues at the frame containing the sync.
        assertArrayEquals(
            stream.copyOfRange(resumeOffset + 8, stream.size),
            resumed.copyOfRange(metadataSize, resumed.size)
        )
    }
}
