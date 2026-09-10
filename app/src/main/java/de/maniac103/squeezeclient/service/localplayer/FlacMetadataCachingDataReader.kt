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
import java.io.IOException
import kotlin.math.min

@UnstableApi
class FlacMetadataCachingDataReader(
    private val upstream: DataReader,
    private val metadataCache: FlacMetadataCache
) : DataReader {
    private val byteReaders by lazy {
        fillMetadataCache() // will be initialized on first read
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val r = byteReaders
            .firstOrNull { !it.isExhausted }
            ?.read(buffer, offset, length)

        return r ?: upstream.read(buffer, offset, length)
    }

    private fun fillMetadataCache(): List<BytesReader> {
        val probe = readExactly(4)

        return if (probe.startsWithStreamStart()) {
            // Full stream: cache probe + following metadata in holder for later injection
            val metadataBytes = readMetadata(probe)
            metadataCache.metadataBytes = metadataBytes
            listOf(BytesReader(metadataBytes))
        } else {
            // After seek: cache only probe locally, inject probe + cached headers
            val cachedBytes = metadataCache.metadataBytes
                ?: throw IOException("Trying to seek without metadata")
            // The stream may resume at an arbitrary byte offset which is not necessarily at a
            // frame boundary, so skip ahead to the next frame sync before replaying the
            // cached metadata.
            listOf(BytesReader(cachedBytes), BytesReader(searchForNextFrameSync(probe)))
        }
    }

    // Returns the first few bytes of stream content starting at the next FLAC frame header start,
    // reading further data from the stream as needed.
    private fun searchForNextFrameSync(probe: ByteArray): ByteArray {
        // First check whether the part of probe that is not covered by window later
        // contains a frame sync
        val probeByteOutsideOfWindow = probe.size - FRAME_HEADER_START_SIZE
        if (probeByteOutsideOfWindow > 0) {
            val matchingIndex = (0 until probeByteOutsideOfWindow).firstOrNull {
                probe.copyOfRange(it, it + FRAME_HEADER_START_SIZE)
                    .startsWithFrameHeaderStart()
            }
            if (matchingIndex != null) {
                return probe.copyOfRange(matchingIndex, probe.size)
            }
        }

        // Create scan window and copy remainder of probe into it
        val window = ByteArray(FRAME_HEADER_START_SIZE)
        val probeBytesWithinWindow = min(probe.size, FRAME_HEADER_START_SIZE)
        probe.copyInto(
            window,
            destinationOffset = window.size - probeBytesWithinWindow,
            startIndex = probe.size - FRAME_HEADER_START_SIZE
        )

        var scannedBytes = 0
        while (!window.startsWithFrameHeaderStart() && scannedBytes++ < MAX_FRAME_SYNC_SCAN_BYTES) {
            // Scan window is small, so calling System.arraycopy (which is a native method)
            // will likely perform worse than this reimplementation
            (0 until window.size - 1).forEach { window[it] = window[it + 1] }
            check(upstream.read(window, window.size - 1, 1) == 1) {
                "Unexpected end of stream while searching for FLAC frame sync"
            }
        }
        if (window.startsWithFrameHeaderStart()) {
            return window
        }
        throw IOException("FLAC frame sync not found within $scannedBytes bytes")
    }

    private fun ByteArray.startsWithStreamStart() = size >= 4 &&
        this[0] == 'f'.code.toByte() &&
        this[1] == 'L'.code.toByte() &&
        this[2] == 'a'.code.toByte() &&
        this[3] == 'C'.code.toByte()

    private fun ByteArray.startsWithFrameHeaderStart(): Boolean {
        if (size < FRAME_HEADER_START_SIZE) {
            return false
        }
        if (this[0] != 0xFF.toByte()) {
            return false
        }
        if ((this[1].toInt() and 0xFE) != 0xF8) {
            return false
        }
        // Third frame header byte: block size code in the high nibble, sample rate code in
        // the low nibble. Both have reserved values which don't occur in real streams, so use
        // them to reject false sync codes inside frame data.
        val blockAndSampleRate = this[2].toUByte().toInt()
        return (blockAndSampleRate and 0xF0) != 0 && (blockAndSampleRate and 0x0F) <= 0x0B
    }

    private fun readMetadata(magic: ByteArray): ByteArray {
        val metadataBlocks = mutableListOf<ByteArray>()
        do {
            val blockHeader = readExactly(4)
            val isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockLength = (blockHeader[1].toUByte().toInt() shl 16) or
                (blockHeader[2].toUByte().toInt() shl 8) or
                (blockHeader[3].toUByte().toInt())
            val blockData = readExactly(blockLength)
            metadataBlocks += (blockHeader + blockData)
        } while (!isLast)

        val totalSize = magic.size + metadataBlocks.sumOf { it.size }
        val result = ByteArray(totalSize)

        magic.copyInto(result)
        var offset = magic.size
        metadataBlocks.forEach { block ->
            block.copyInto(result, offset)
            offset += block.size
        }

        return result
    }

    private fun readExactly(length: Int): ByteArray {
        val data = ByteArray(length)
        var position = 0
        while (position < length) {
            val bytesRead = upstream.read(data, position, data.size - position)
            check(bytesRead != C.RESULT_END_OF_INPUT) {
                "Not enough data could be read: $position < $length"
            }
            position += bytesRead
        }
        return data
    }

    companion object {
        // Number of bytes needed to identify a FLAC frame header start: sync code, reserved
        // and blocking strategy bits, block size and sample rate codes.
        private const val FRAME_HEADER_START_SIZE = 3

        // Generous upper bound: FLAC frames are at most a few hundred KB in practice.
        private const val MAX_FRAME_SYNC_SCAN_BYTES = 4 * 1024 * 1024

        private class BytesReader(private val data: ByteArray) : DataReader {
            private var position = 0
            val isExhausted get() = position == data.size

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (isExhausted) {
                    return C.RESULT_END_OF_INPUT
                }
                val bytesToCopy = min(length, data.size - position)
                data.copyInto(buffer, offset, position, position + bytesToCopy)
                position += bytesToCopy
                return bytesToCopy
            }
        }
    }
}

class FlacMetadataCache {
    internal var metadataBytes: ByteArray? = null
}
