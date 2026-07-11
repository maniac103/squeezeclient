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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.extractor.metadata.icy.IcyHeaders
import java.io.IOException
import kotlin.math.min

@UnstableApi
class FlacMetadataCachingDataSource(
    private val upstream: DataSource,
    private val metadataCache: FlacMetadataCache
) : DataSource {
    private val dataReaders = mutableListOf<BytesReader>()

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        // Make sure we don't receive ICY headers, because they mess both with our metadata parsing
        // and our cached metadata replay. Since we're dealing with local files here, they are
        // meaningless anyway.
        val newHeaders = dataSpec.httpRequestHeaders
            .toMutableMap()
            .apply { remove(IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME) }

        val newDataSpec = dataSpec.buildUpon()
            .setHttpRequestHeaders(newHeaders)
            .build()

        val length = upstream.open(newDataSpec)
        val probe = DataSourceUtil.readExactly(upstream, 4)

        return if (
            probe[0] == 'f'.code.toByte() &&
            probe[1] == 'L'.code.toByte() &&
            probe[2] == 'a'.code.toByte() &&
            probe[3] == 'C'.code.toByte()
        ) {
            // Full stream: cache probe + following metadata in holder for later injection
            val metadataBytes = readMetadata(upstream, probe)
            dataReaders.add(BytesReader(metadataBytes))
            metadataCache.metadataBytes = metadataBytes
            length
        } else {
            // After seek: cache only probe locally, inject probe + cached headers
            val cachedBytes = metadataCache.metadataBytes
                ?: throw IOException("Trying to seek without metadata")
            dataReaders.add(BytesReader(cachedBytes))
            dataReaders.add(BytesReader(probe))
            if (length != C.LENGTH_UNSET.toLong()) {
                length + cachedBytes.size
            } else {
                length
            }
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun getUri() = upstream.uri

    override fun close() {
        upstream.close()
        dataReaders.clear()
    }

    override fun getResponseHeaders() = upstream.responseHeaders

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        val r = dataReaders
            .firstOrNull { !it.isExhausted }
            ?.read(buffer, offset, length)

        return r ?: upstream.read(buffer, offset, length)
    }

    private fun readMetadata(source: DataSource, magic: ByteArray): ByteArray {
        val metadataBlocks = mutableListOf<ByteArray>()
        do {
            val blockHeader = DataSourceUtil.readExactly(source, 4)
            val isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockLength = (blockHeader[1].toUByte().toInt() shl 16) or
                    (blockHeader[2].toUByte().toInt() shl 8) or
                    (blockHeader[3].toUByte().toInt())
            val blockData = DataSourceUtil.readExactly(source, blockLength)
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
