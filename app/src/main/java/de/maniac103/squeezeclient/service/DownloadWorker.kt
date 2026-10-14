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
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.maniac103.squeezeclient.BuildConfig
import de.maniac103.squeezeclient.NotificationActionReceiver
import de.maniac103.squeezeclient.Preferences
import de.maniac103.squeezeclient.R
import de.maniac103.squeezeclient.database.DownloadEntity
import de.maniac103.squeezeclient.database.DownloadState
import de.maniac103.squeezeclient.database.SqueezeClientDatabase
import de.maniac103.squeezeclient.extfuncs.getOrCreateNotificationChannel
import de.maniac103.squeezeclient.model.DownloadSongInfo
import de.maniac103.squeezeclient.model.ServerConfiguration
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import okio.sink
import okio.use
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val preferences: Preferences,
    private val httpClient: OkHttpClient,
    private val database: SqueezeClientDatabase
) : CoroutineWorker(context, params) {
    private var progress = Data.EMPTY

    override suspend fun doWork(): Result {
        val serverConfig = preferences.serverConfig.value
            ?: return Result.failure()
        val dao = database.downloadDao()
        val folderStructure = preferences.downloadFolderStructure.value

        dao.resetInProgress()

        var finishedCount = 0
        while (true) {
            val entry = dao.claimNext() ?: break
            val item = entry.toSongInfo()
            val total = finishedCount + 1 + dao.pendingCount()

            try {
                downloadOneFile(item, serverConfig, folderStructure, finishedCount, total)
                dao.delete(entry.id)
            } catch (e: Exception) {
                Log.w(TAG, "Downloading item $item failed", e)
                dao.setState(entry.id, DownloadState.FAILED)
            }
            ++finishedCount
        }

        return Result.success()
    }

    private suspend fun downloadOneFile(
        item: DownloadSongInfo,
        serverConfig: ServerConfiguration,
        folderStructure: Preferences.DownloadFolderStructure,
        index: Int,
        totalCount: Int
    ) {
        val resolver = applicationContext.contentResolver

        updateProgress(item, 0, null, index, totalCount)
        if (item.isAlreadyPresent()) {
            return
        }

        val songUrl = serverConfig.url.newBuilder()
            .addPathSegment("music")
            .addPathSegment(item.trackId.toString())
            .addPathSegment("download")
            .build()
        val request = Request.Builder()
            .url(songUrl)
            .build()

        withContext(Dispatchers.IO) {
            val responseBody = httpClient.newCall(request)
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
                                updateProgress(item, bytesRead, total, index, totalCount)
                            }
                        }
                        stream.sink().buffer().use {
                            it.writeAll(source)
                        }
                    }
                    val values = contentValuesOf(Media.IS_PENDING to 0)
                    resolver.update(insertUri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(insertUri, null, null)
                    throw e
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext

        val cancelPi = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NOTIFICATION_ACTION_CANCEL_DOWNLOAD)
            .let { intent -> PendingIntentCompat.getBroadcast(context, 0, intent, 0, false) }

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
            .addAction(R.drawable.ic_close_24dp, cancelActionText, cancelPi)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NotificationIds.DOWNLOAD_WORKER,
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
        folderStructure: Preferences.DownloadFolderStructure
    ): Uri? {
        val pathSegments = relativeStoragePath.toUri().pathSegments
        val artistForFolder = albumArtist ?: artist

        val relativePath = when (folderStructure) {
            Preferences.DownloadFolderStructure.Artist -> artistForFolder

            Preferences.DownloadFolderStructure.Album -> album

            Preferences.DownloadFolderStructure.ArtistAlbum -> "$artistForFolder - $album"

            Preferences.DownloadFolderStructure.AlbumUnderArtist -> "$artistForFolder/$album"

            Preferences.DownloadFolderStructure.AsOnServer -> {
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

    companion object : KoinComponent {
        private const val TAG = "DownloadWorker"
        private const val WORK_NAME = "song_download"

        const val NOTIFICATION_ACTION_DISMISS_FAILED =
            BuildConfig.APPLICATION_ID + ".action.DISMISS_FAILED"
        const val NOTIFICATION_ACTION_RETRY_DOWNLOAD =
            BuildConfig.APPLICATION_ID + ".action.RETRY_DOWNLOAD"
        const val NOTIFICATION_ACTION_CANCEL_DOWNLOAD =
            BuildConfig.APPLICATION_ID + ".action.CANCEL_DOWNLOAD"

        suspend fun enqueue(items: List<DownloadSongInfo>) {
            val database = get<SqueezeClientDatabase>()
            database.downloadDao().insertAll(items.map { DownloadEntity(it) })
            enqueueWorker()
        }

        suspend fun observeStatus(context: Context) {
            val notifManager = NotificationManagerCompat.from(context)
            val database = get<SqueezeClientDatabase>()

            database.downloadDao().observeFailedCount().collect { count ->
                val notificationId = NotificationIds.DOWNLOAD_FAILURE
                val channel = context.getOrCreateDownloadWorkerNotificationChannel()
                if (count > 0) {
                    notifManager.notify(
                        notificationId,
                        createDownloadRetryNotification(context, count, channel.id)
                    )
                } else {
                    notifManager.cancel(notificationId)
                }
            }
        }

        suspend fun handleRetryAction() {
            val database = get<SqueezeClientDatabase>()
            database.downloadDao().retryFailed()
            enqueueWorker()
        }

        suspend fun handleDismissAction() {
            val database = get<SqueezeClientDatabase>()
            database.downloadDao().deleteFailed()
        }

        suspend fun handleCancelAction() {
            val database = get<SqueezeClientDatabase>()
            val workManager = get<WorkManager>()
            database.downloadDao().deleteUnfinished()
            workManager.cancelUniqueWork(WORK_NAME)
        }

        private fun enqueueWorker() {
            val workManager = get<WorkManager>()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(
                uniqueWorkName = WORK_NAME,
                existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
                request = request
            )
        }

        private fun createDownloadRetryNotification(
            context: Context,
            failedCount: Int,
            channelId: String
        ): Notification {
            val retryPi = Intent(context, NotificationActionReceiver::class.java)
                .setAction(NOTIFICATION_ACTION_RETRY_DOWNLOAD)
                .let { intent ->
                    PendingIntentCompat.getBroadcast(context, 0, intent, 0, false)
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
                        failedCount,
                        failedCount
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
