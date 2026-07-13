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

package de.maniac103.squeezeclient.service.localplayer;

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ProgressiveMediaExtractor
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.ts.AdtsExtractor
import kotlin.collections.emptyList

@UnstableApi
class LocalPlayerMediaExtractor(
    private val mimeType: String,
    private val flacMetadataCache: FlacMetadataCache
) : ProgressiveMediaExtractor {
    private var extractor: Extractor? = null
    private var extractorInput: ExtractorInput? = null

    override fun init(
        dataReader: DataReader,
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
        position: Long,
        length: Long,
        output: ExtractorOutput
    ) {
        val extractor = when (mimeType) {
            "audio/mpeg" -> Mp3Extractor()
            "audio/flac" -> FlacExtractor()
            "audio/ogg" -> OggExtractor()
            "audio/mp4a-latm" -> AdtsExtractor()
            else -> throw UnrecognizedInputFormatException(
                "Unknown audio mime type $mimeType",
                uri,
                emptyList()
            )
        }
        val actualDataReader = if (uri.path == "/stream.mp3" && mimeType == "audio/flac") {
            FlacMetadataCachingDataReader(dataReader, flacMetadataCache)
        } else {
            dataReader
        }
        extractor.init(output)
        this.extractor = extractor
        this.extractorInput = DefaultExtractorInput(actualDataReader, position, length)
    }

    override fun release() {
        extractor?.release()
        extractor = null
        extractorInput = null
    }

    override fun disableSeekingOnMp3Streams() {
        val underlying = extractor?.underlyingImplementation
        (underlying as? Mp3Extractor)?.disableSeeking()
    }

    override fun getCurrentInputPosition() =
        extractorInput?.position ?: C.INDEX_UNSET.toLong()

    override fun seek(position: Long, seekTimeUs: Long) {
        extractor?.seek(position, seekTimeUs)
    }

    override fun read(positionHolder: PositionHolder): Int {
        val input = requireNotNull(extractorInput)
        return requireNotNull(extractor).read(input, positionHolder)
    }
}
