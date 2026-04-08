package com.arflix.tv.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arflix.tv.R
import com.arflix.tv.data.db.DownloadDao
import com.arflix.tv.data.db.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_TITLE = "title"
        const val KEY_SUBTITLE_URL = "subtitle_url"
        private const val TAG = "DownloadWorker"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID_BASE = 10000
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL = 500L // ms
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1)
        val streamUrl = inputData.getString(KEY_STREAM_URL)
        val title = inputData.getString(KEY_TITLE) ?: "Download"

        if (downloadId == -1L || streamUrl.isNullOrBlank()) {
            Log.e(TAG, "Missing download parameters")
            return@withContext Result.failure()
        }

        val download = downloadDao.getById(downloadId)
        if (download == null) {
            Log.e(TAG, "Download $downloadId not found in DB")
            return@withContext Result.failure()
        }

        createNotificationChannel()

        try {
            setForeground(createForegroundInfo(title, 0))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground: ${e.message}")
        }

        downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

        try {
            // Use external app-specific directory (accessible via file explorer)
            // Path: /sdcard/Android/data/com.arflix.tv/files/downloads/
            val downloadsDir = File(applicationContext.getExternalFilesDir(null), "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val extension = guessExtension(streamUrl)
            val fileName = "download_${downloadId}$extension"
            val outputFile = File(downloadsDir, fileName)

            val request = Request.Builder().url(streamUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, "HTTP ${response.code}")
                return@withContext Result.retry()
            }

            val body = response.body ?: run {
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, "Empty response body")
                return@withContext Result.retry()
            }

            val contentLength = body.contentLength()
            var bytesDownloaded = 0L
            var lastProgressUpdate = 0L

            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED, "Paused")
                            return@withContext Result.success()
                        }

                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                            lastProgressUpdate = now
                            val progress = if (contentLength > 0) {
                                ((bytesDownloaded * 100) / contentLength).toInt().coerceIn(0, 100)
                            } else 0

                            downloadDao.updateProgress(
                                downloadId, DownloadStatus.DOWNLOADING, progress, bytesDownloaded
                            )

                            try {
                                setForeground(createForegroundInfo(title, progress))
                            } catch (_: Exception) { }
                        }
                    }
                }
            }

            downloadDao.markCompleted(
                id = downloadId,
                status = DownloadStatus.COMPLETED,
                localUri = outputFile.absolutePath,
                fileSize = bytesDownloaded,
                completedAt = System.currentTimeMillis()
            )

            // Download subtitle file if requested
            val subtitleUrl = inputData.getString(KEY_SUBTITLE_URL)
            if (!subtitleUrl.isNullOrBlank()) {
                try {
                    val subExt = guessSubtitleExtension(subtitleUrl)
                    val subFile = File(downloadsDir, "subtitle_${downloadId}$subExt")
                    val subRequest = Request.Builder().url(subtitleUrl).build()
                    val subResponse = okHttpClient.newCall(subRequest).execute()
                    if (subResponse.isSuccessful) {
                        subResponse.body?.byteStream()?.use { input ->
                            subFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        downloadDao.updateSubtitleUri(downloadId, subFile.absolutePath)
                        Log.d(TAG, "Subtitle downloaded: ${subFile.name}")
                    } else {
                        Log.w(TAG, "Subtitle download failed: HTTP ${subResponse.code}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Subtitle download error (non-fatal): ${e.message}")
                }
            }

            Log.d(TAG, "Download complete: $title (${bytesDownloaded / 1024 / 1024} MB)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, e.message)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media download progress"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notificationId = NOTIFICATION_ID_BASE + inputData.getLong(KEY_DOWNLOAD_ID, 0).toInt()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun guessSubtitleExtension(url: String): String {
        val lower = url.lowercase().substringBefore("?")
        return when {
            lower.endsWith(".vtt") -> ".vtt"
            lower.endsWith(".ass") -> ".ass"
            lower.endsWith(".ssa") -> ".ssa"
            else -> ".srt"
        }
    }

    private fun guessExtension(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".mp4") -> ".mp4"
            lower.contains(".mkv") -> ".mkv"
            lower.contains(".m3u8") -> ".m3u8"
            lower.contains(".mpd") -> ".mpd"
            else -> ".mp4"
        }
    }
}
