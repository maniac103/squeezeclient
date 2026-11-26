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

package de.maniac103.squeezeclient.database

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import de.maniac103.squeezeclient.model.DownloadSongInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow

enum class DownloadState { PENDING, IN_PROGRESS, FAILED }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val relativeStoragePath: String,
    val durationMs: Long,
    val state: DownloadState = DownloadState.PENDING
) {
    constructor(info: DownloadSongInfo) : this(
        trackId = info.trackId,
        title = info.title,
        artist = info.artist,
        album = info.album,
        albumArtist = info.albumArtist,
        relativeStoragePath = info.relativeStoragePath,
        durationMs = info.duration.inWholeMilliseconds
    )

    fun toSongInfo() = DownloadSongInfo(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        relativeStoragePath = relativeStoragePath,
        duration = durationMs.milliseconds
    )
}

@Dao
interface DownloadDao {
    @Insert suspend fun insertAll(items: List<DownloadEntity>)

    @Query("SELECT * FROM downloads WHERE state = 'PENDING' ORDER BY id LIMIT 1")
    suspend fun nextPending(): DownloadEntity?

    @Query("UPDATE downloads SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: DownloadState)

    @Transaction
    suspend fun claimNext(): DownloadEntity? {
        val entry = nextPending() ?: return null
        setState(entry.id, DownloadState.IN_PROGRESS)
        return entry
    }

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE downloads SET state = 'PENDING' WHERE state = 'IN_PROGRESS'")
    suspend fun resetInProgress()

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("UPDATE downloads SET state = 'PENDING' WHERE state = 'FAILED'")
    suspend fun retryFailed()

    @Query("DELETE FROM downloads WHERE state = 'FAILED'")
    suspend fun deleteFailed()

    @Query("DELETE FROM downloads WHERE state != 'FAILED'")
    suspend fun deleteUnfinished()
}
