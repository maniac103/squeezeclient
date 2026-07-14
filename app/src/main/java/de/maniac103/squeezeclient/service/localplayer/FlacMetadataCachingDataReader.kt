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

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        val r = byteReaders
            .firstOrNull { !it.isExhausted }
            ?.read(buffer, offset, length)

        return r ?: upstream.read(buffer, offset, length)
    }

    private fun fillMetadataCache(): List<BytesReader> {
        val probe = readExactly(4)

        return if (
            probe[0] == 'f'.code.toByte() &&
            probe[1] == 'L'.code.toByte() &&
            probe[2] == 'a'.code.toByte() &&
            probe[3] == 'C'.code.toByte()
        ) {
            // Full stream: cache probe + following metadata in holder for later injection
            val metadataBytes = readMetadata(probe)
            metadataCache.metadataBytes = metadataBytes
            listOf(BytesReader(metadataBytes))
        } else {
            // After seek: cache only probe locally, inject probe + cached headers
            val cachedBytes = metadataCache.metadataBytes
                ?: throw IOException("Trying to seek without metadata")
            listOf(BytesReader(cachedBytes), BytesReader(probe))
        }
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
        private class BytesReader(private val data: ByteArray): DataReader {
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
