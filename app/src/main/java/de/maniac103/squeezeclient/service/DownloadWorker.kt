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

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore.Audio.Media
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.contentValuesOf
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.NotificationActionReceiver
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.extfuncs.DownloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.downloadFolderStructure
import de.maniac103.squeezeclient.extfuncs.getOrCreateNotificationChannel
import de.maniac103.squeezeclient.extfuncs.httpClient
import de.maniac103.squeezeclient.extfuncs.jsonParser
import de.maniac103.squeezeclient.extfuncs.prefs
import de.maniac103.squeezeclient.extfuncs.serverConfig
import de.maniac103.squeezeclient.extfuncs.workManager
import de.maniac103.squeezeclient.model.DownloadSongInfo
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import okio.sink
import okio.use

class DownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private var progress = Data.EMPTY

    override suspend fun doWork(): Result {
        val items = inputData.getStringArray(InputDataKeys.ITEMS)
            ?.toSongInfos(applicationContext)
            ?: return Result.success()
        val serverConfig = applicationContext.prefs.serverConfig
            ?: return Result.failure()
        val baseUrl = serverConfig.url
        val client = applicationContext.httpClient
        val resolver = applicationContext.contentResolver
        val folderStructure = applicationContext.prefs.downloadFolderStructure

        Log.d(TAG, "Starting download of ${items.size} items")

        val results = items.mapIndexed { index, item ->
            updateProgress(item, 0, null, index, items.size)
            if (item.isAlreadyPresent()) {
                return@mapIndexed DownloadResult.AlreadyPresent(item)
            }

            val songUrl = baseUrl.newBuilder()
                .addPathSegment("music")
                .addPathSegment(item.trackId.toString())
                .addPathSegment("download")
                .build()
            val request = Request.Builder()
                .url(songUrl)
                .build()

            try {
                withContext(Dispatchers.IO) {
                    val responseBody = client.newCall(request)
                        .execute()
                        .body

                    responseBody.use { body ->
                        val mimeType = body.contentType()?.toString()
                        val insertUri = item.insertIntoMediaProvider(mimeType, folderStructure)
                            ?: throw RuntimeException("Media provider insertion failed")
                        try {
                            resolver.openOutputStream(insertUri)?.use { stream ->
                                val source = ProgressReportingSource(body) { bytesRead, total ->
                                    launch {
                                        updateProgress(item, bytesRead, total, index, items.size)
                                    }
                                }
                                stream.sink().buffer().use {
                                    it.writeAll(source)
                                }
                            }
                            val values = contentValuesOf(Media.IS_PENDING to 0)
                            resolver.update(insertUri, values, null, null)
                            DownloadResult.Success(item)
                        } catch (e: Exception) {
                            resolver.delete(insertUri, null, null)
                            throw e
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Downloading item $item failed", e)
                DownloadResult.Failure(item)
            }
        }

        val failed = results.filterIsInstance<DownloadResult.Failure>()
        val resultDataBuilder = Data.Builder()
            .putInt(OutputDataKeys.SUCCESS_COUNT, results.count { it is DownloadResult.Success })
            .putInt(
                OutputDataKeys.SKIP_COUNT,
                results.count { it is DownloadResult.AlreadyPresent }
            )

        return if (failed.isNotEmpty()) {
            resultDataBuilder.putStringArray(
                OutputDataKeys.FAILED_ITEMS,
                failed.map { applicationContext.jsonParser.encodeToString(it.item) }.toTypedArray()
            )
            Result.failure(resultDataBuilder.build())
        } else {
            Result.success(resultDataBuilder.build())
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val cancelIntent = context.workManager.createCancelPendingIntent(id)
        val cancelActionText = context.getString(R.string.download_notification_action_cancel)
        val finishedItems = progress.getInt(ProgressKeys.ITEMS_DONE, 0)
        val totalItems = progress.getInt(ProgressKeys.ITEMS_TOTAL, -1)
        val itemProgress = progress.getInt(ProgressKeys.CURRENT_ITEM_PROGRESS, 0)
        val currentItem = progress.getString(ProgressKeys.CURRENT_ITEM)
        val title = context.getString(
            R.string.download_notification_title,
            finishedItems + 1,
            totalItems
        )
        val channel = context.getOrCreateDownloadWorkerNotificationChannel()
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_download_24dp)
            .setContentTitle(title)
            .setContentText(currentItem)
            .setProgress(totalItems * 100, finishedItems * 100 + itemProgress, totalItems < 0)
            .addAction(R.drawable.ic_close_24dp, cancelActionText, cancelIntent)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NotificationIds.forDownloadWork(id),
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
            ProgressKeys.ITEMS_DONE to titleIndex,
            ProgressKeys.ITEMS_TOTAL to titleCount,
            ProgressKeys.CURRENT_ITEM_PROGRESS to currentItemProgress,
            ProgressKeys.CURRENT_ITEM to item.title
        )
        setProgress(progress)
        setForeground(getForegroundInfo())
    }

    @OptIn(ExperimentalTime::class)
    class ProgressReportingSource(
        body: ResponseBody,
        private val progressConsumer: (bytesRead: Long, totalLength: Long) -> Unit
    ) : ForwardingSource(body.source()) {
        private val contentLength = body.contentLength()
        private var totalBytesRead = 0L
        private var lastProgressUpdate = Clock.System.now()

        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            // read() returns the number of bytes read, or -1 if this source is exhausted.
            totalBytesRead += if (bytesRead != -1L) bytesRead else 0
            val now = Clock.System.now()
            if (now - lastProgressUpdate > 2.seconds && contentLength > 0) {
                progressConsumer(totalBytesRead, contentLength)
                lastProgressUpdate = now
            }
            return bytesRead
        }
    }

    private fun DownloadSongInfo.insertIntoMediaProvider(
        mimeType: String?,
        folderStructure: DownloadFolderStructure
    ): Uri? {
        val pathSegments = relativeStoragePath.toUri().pathSegments
        val relativePath = when (folderStructure) {
            DownloadFolderStructure.Artist -> artist
            DownloadFolderStructure.Album -> album
            DownloadFolderStructure.ArtistAlbum -> "$artist - $album"
            DownloadFolderStructure.AlbumUnderArtist -> "$artist/$album"
            DownloadFolderStructure.AsOnServer -> {
                pathSegments.dropLast(1).joinToString(separator = "/")
            }
        }
        val values = contentValuesOf(
            Media.ALBUM to album,
            Media.ARTIST to artist,
            Media.ALBUM_ARTIST to albumArtist,
            Media.TITLE to title,
            Media.DISPLAY_NAME to pathSegments.last(),
            Media.MIME_TYPE to mimeType,
            Media.RELATIVE_PATH to "${Environment.DIRECTORY_MUSIC}/$relativePath",
            Media.IS_PENDING to 1
        )
        val resolver = applicationContext.contentResolver
        return resolver.insert(Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun DownloadSongInfo.isAlreadyPresent(): Boolean {
        val existingItemCursor = applicationContext.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI,
            arrayOf(Media._ID),
            "${Media.ALBUM}=? AND ${Media.ARTIST}=? AND ${Media.TITLE}=? AND ${Media.IS_PENDING}=0",
            arrayOf(album, artist, title),
            null,
            null
        )
        val itemIsPresent = existingItemCursor?.count?.let { it > 0 }
        existingItemCursor?.close()
        return itemIsPresent == true
    }

    private object ProgressKeys {
        const val ITEMS_DONE = "items_done"
        const val ITEMS_TOTAL = "items_total"
        const val CURRENT_ITEM_PROGRESS = "current_item_progress"
        const val CURRENT_ITEM = "current_item"
    }

    private object InputDataKeys {
        const val ITEMS = "items"
    }

    private object OutputDataKeys {
        const val SUCCESS_COUNT = "succeeded"
        const val SKIP_COUNT = "skipped"
        const val FAILED_ITEMS = "failed_items"
    }

    sealed class DownloadResult(val item: DownloadSongInfo) {
        class Success(item: DownloadSongInfo) : DownloadResult(item)
        class AlreadyPresent(item: DownloadSongInfo) : DownloadResult(item)
        class Failure(item: DownloadSongInfo) : DownloadResult(item)
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private const val WORK_TAG = "song_download"

        const val NOTIFICATION_ACTION_DISMISS_FAILED =
            BuildConfig.APPLICATION_ID + ".action.DISMISS_FAILED"
        const val NOTIFICATION_ACTION_RETRY_DOWNLOAD =
            BuildConfig.APPLICATION_ID + ".action.RETRY_DOWNLOAD"
        private const val RETRY_DOWNLOAD_EXTRA_NOTIFICATION_ID = "notification_id"
        private const val RETRY_DOWNLOAD_EXTRA_ITEMS = "items"

        private fun List<DownloadSongInfo>.toDataValue(context: Context): Array<String?> =
            map { context.jsonParser.encodeToString(it) }.toTypedArray()
        private fun Array<String>.toSongInfos(context: Context) =
            map { context.jsonParser.decodeFromString<DownloadSongInfo>(it) }

        fun enqueue(context: Context, items: List<DownloadSongInfo>) {
            val inputData = Data.Builder()
                .putStringArray(InputDataKeys.ITEMS, items.toDataValue(context))
                .build()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .build()
            context.workManager.enqueue(request)
        }

        fun startObservingStatus(context: Context) {
            val notifManager = NotificationManagerCompat.from(context)
            val workManager = context.workManager

            workManager.getWorkInfosByTagLiveData(WORK_TAG).observeForever { workInfos ->
                val failedEntries = workInfos.filter { it.state == WorkInfo.State.FAILED }
                if (failedEntries.isEmpty()) {
                    return@observeForever
                }

                val notificationId = NotificationIds.forDownloadWork(failedEntries.first().id)
                val failedItems = failedEntries
                    .mapNotNull { it.outputData.getStringArray(OutputDataKeys.FAILED_ITEMS) }
                    .toTypedArray()
                    .flatten()
                val channel = context.getOrCreateDownloadWorkerNotificationChannel()
                val notification = createDownloadRetryNotification(
                    context,
                    failedItems,
                    notificationId,
                    channel.id
                )
                notifManager.notify(notificationId, notification)
            }
        }

        fun handleRetryAction(context: Context, intent: Intent) {
            intent.getStringArrayExtra(RETRY_DOWNLOAD_EXTRA_ITEMS)
                ?.toSongInfos(context)
                ?.let { infos -> enqueue(context, infos) }
            // Since we re-enqueued the failed downloads,we can now safely remove the old entries
            context.workManager.pruneWork()
            val nm = context.getSystemService<NotificationManager>()
            nm?.cancel(intent.getIntExtra(RETRY_DOWNLOAD_EXTRA_NOTIFICATION_ID, 0))
        }

        fun handleDismissAction(context: Context) {
            context.workManager.pruneWork()
        }

        private fun createDownloadRetryNotification(
            context: Context,
            itemData: List<String>,
            notificationId: Int,
            channelId: String
        ): Notification {
            val retryPi = Intent(context, NotificationActionReceiver::class.java)
                .setAction(NOTIFICATION_ACTION_RETRY_DOWNLOAD)
                .putExtra(RETRY_DOWNLOAD_EXTRA_ITEMS, itemData.toTypedArray())
                .putExtra(RETRY_DOWNLOAD_EXTRA_NOTIFICATION_ID, notificationId)
                .let { intent ->
                    PendingIntentCompat.getBroadcast(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        false
                    )
                }

            val dismissPi = Intent(context, NotificationActionReceiver::class.java)
                .setAction(NOTIFICATION_ACTION_DISMISS_FAILED)
                .let { intent -> PendingIntentCompat.getBroadcast(context, 0, intent, 0, false) }

            return NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_logo_notification_24dp)
                .setContentTitle(context.getString(R.string.download_failure_notification_title))
                .setContentText(
                    context.resources.getQuantityString(
                        R.plurals.download_failure_notification_text,
                        itemData.size,
                        itemData.size
                    )
                )
                .setDeleteIntent(dismissPi)
                .addAction(
                    R.drawable.ic_refresh_24dp,
                    context.getString(R.string.download_failure_notification_action_retry),
                    retryPi
                )
                .build()
        }

        private fun Context.getOrCreateDownloadWorkerNotificationChannel() =
            NotificationManagerCompat.from(this).getOrCreateNotificationChannel(
                resources,
                NotificationIds.CHANNEL_DOWNLOAD
            )
    }
}
