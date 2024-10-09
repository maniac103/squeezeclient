/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
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

package de.maniac103.squeezeclient.service

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Environment
import android.provider.MediaStore.Audio.Media
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.DownloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.downloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.jsonParser
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.model.DownloadSongInfo
import java.io.OutputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import okio.sink

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private var progress = Data.EMPTY

    override suspend fun doWork(): Result {
        val items = inputData.getStringArray("items")
            ?.map { applicationContext.jsonParser.decodeFromString<DownloadSongInfo>(it) }
            ?: return Result.success()
        val serverConfig = applicationContext.prefs.serverConfig
            ?: return Result.failure()
        val baseUrl = serverConfig.url
        val client = applicationContext.httpClient
        val resolver = applicationContext.contentResolver
        val folderStructure = applicationContext.prefs.downloadFolderStructure

        createNotificationChannelIfNeeded()

        items.forEachIndexed { index, item ->
            updateProgress(item, 0, null, index, items.size)
            if (isItemAlreadyPresent(item)) {
                return@forEachIndexed
            }

            val songUrl = baseUrl.newBuilder()
                .addPathSegment("music")
                .addPathSegment(item.trackId.toString())
                .addPathSegment("download")
                .build()
            val request = Request.Builder()
                .url(songUrl)
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    // TODO: handle error
                    return@withContext
                }
                response.body?.use { body ->
                    val mimeType = body.contentType()?.toString()
                    val (relativePath, fileName) =
                        determineRelativeStoragePath(item, folderStructure)

                    val values = ContentValues().apply {
                        put(Media.ALBUM, item.album)
                        put(Media.ARTIST, item.artist)
                        put(Media.ALBUM_ARTIST, item.albumArtist)
                        put(Media.TITLE, item.title)
                        put(Media.DISPLAY_NAME, fileName)
                        mimeType?.let { put(Media.MIME_TYPE, it) }
                        put(Media.RELATIVE_PATH, relativePath)
                        put(Media.IS_PENDING, 1)
                    }
                    val insertUri = resolver.insert(Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext

                    try {
                        resolver.openOutputStream(insertUri)?.use { stream ->
                            downloadItem(this, item, stream, body, index, items.size)
                        }
                        values.clear()
                        values.put(Media.IS_PENDING, 0)
                        resolver.update(insertUri, values, null, null)
                    } catch (e: Exception) {
                        resolver.delete(insertUri, null, null)
                        throw e
                    }
                }
            }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val cancelActionText = context.getString(R.string.download_notification_action_cancel)
        val finishedItems = progress.getInt(Progress.ITEMS_DONE, 0)
        val totalItems = progress.getInt(Progress.ITEMS_TOTAL, -1)
        val itemProgress = progress.getInt(Progress.CURRENT_ITEM_PROGRESS, 0)
        val currentTitle = progress.getString(Progress.CURRENT_ITEM)
        val content = context.getString(
            R.string.download_notification_content,
            finishedItems + 1,
            totalItems
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_download_24dp)
            .setContentTitle(currentTitle)
            .setTicker(currentTitle)
            .setContentText(content)
            .setSubText(context.getString(R.string.download_notification_subtext))
            .setProgress(totalItems * 100, finishedItems * 100 + itemProgress, totalItems < 0)
            .addAction(R.drawable.ic_close_24dp, cancelActionText, cancelIntent)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            id.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun updateProgress(
        item: DownloadSongInfo,
        bytes: Long,
        contentLength: Long?,
        titleIndex: Int,
        titleCount: Int
    ) {
        val currentItemProgress = contentLength?.let { (bytes * 100 / it).toInt() } ?: 0
        progress = workDataOf(
            Progress.ITEMS_DONE to titleIndex,
            Progress.ITEMS_TOTAL to titleCount,
            Progress.CURRENT_ITEM_PROGRESS to currentItemProgress,
            Progress.CURRENT_ITEM to item.title
        )
        setProgress(progress)
        setForeground(getForegroundInfo())
    }

    private fun determineRelativeStoragePath(
        item: DownloadSongInfo,
        folderStructure: DownloadFolderStructure
    ): Pair<String, String> {
        val pathSegments = item.relativeStoragePath.toUri().pathSegments
        val relativePath = when (folderStructure) {
            DownloadFolderStructure.Artist -> item.artist
            DownloadFolderStructure.Album -> item.album
            DownloadFolderStructure.ArtistAlbum -> "${item.artist} - ${item.album}"
            DownloadFolderStructure.AlbumUnderArtist -> "${item.artist}/${item.album}"
            DownloadFolderStructure.AsOnServer -> {
                pathSegments.dropLast(1).joinToString(separator = "/")
            }
        }
        return Pair("${Environment.DIRECTORY_MUSIC}/$relativePath", pathSegments.last())
    }

    private fun createNotificationChannelIfNeeded() {
        val context = applicationContext
        val nm = NotificationManagerCompat.from(context)
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL) != null) {
            return
        }
        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).apply {
            setName(context.getString(R.string.download_notification_channel_name))
            setDescription(context.getString(R.string.download_notification_channel_description))
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setSound(null, null)
        }.build()
        nm.createNotificationChannel(channel)
    }

    private fun isItemAlreadyPresent(item: DownloadSongInfo): Boolean {
        val existingItemCursor = applicationContext.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI,
            arrayOf(Media._ID),
            "${Media.ALBUM}=? AND ${Media.ARTIST}=? AND ${Media.TITLE}=? AND ${Media.IS_PENDING}=0",
            arrayOf(item.album, item.artist, item.title),
            null,
            null
        )
        val itemIsPresent = existingItemCursor?.count?.let { it > 0 }
        existingItemCursor?.close()
        return itemIsPresent == true
    }

    private fun downloadItem(
        scope: CoroutineScope,
        item: DownloadSongInfo,
        target: OutputStream,
        body: ResponseBody,
        index: Int,
        totalCount: Int
    ) {
        val source = object : ForwardingSource(body.source()) {
            private var totalBytesRead = 0L
            private var lastProgressUpdate = Clock.System.now()

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                val now = Clock.System.now()
                val contentLength = body.contentLength()
                if (now - lastProgressUpdate > 5.seconds && contentLength > 0) {
                    scope.launch {
                        updateProgress(item, totalBytesRead, contentLength, index, totalCount)
                    }
                    lastProgressUpdate = now
                }
                return bytesRead
            }
        }
        val sink = target.sink().buffer()
        sink.writeAll(source)
        sink.close()
    }

    object Progress {
        const val ITEMS_DONE = "items_done"
        const val ITEMS_TOTAL = "items_total"
        const val CURRENT_ITEM_PROGRESS = "current_item_progress"
        const val CURRENT_ITEM = "current_item"
    }

    companion object {
        private const val NOTIFICATION_CHANNEL = "background_download"

        fun buildRequest(context: Context, items: List<DownloadSongInfo>): OneTimeWorkRequest {
            val dataJsonArray = items.map { context.jsonParser.encodeToString(it) }.toTypedArray()
            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInputData(Data.Builder().putStringArray("items", dataJsonArray).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }
    }
}
