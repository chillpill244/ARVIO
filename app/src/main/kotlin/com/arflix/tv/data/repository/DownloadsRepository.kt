package com.arflix.tv.data.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.arflix.tv.data.db.DownloadDao
import com.arflix.tv.data.db.DownloadEntity
import com.arflix.tv.data.db.DownloadStatus
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val TAG = "DownloadsRepository"
    }

    fun observeAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.observeAll()

    fun observeDownloadsForMedia(tmdbId: Int, mediaType: MediaType): Flow<List<DownloadEntity>> =
        downloadDao.observeByTmdbId(tmdbId, mediaType.name)

    suspend fun getAllDownloads(): List<DownloadEntity> = downloadDao.getAll()

    suspend fun getDownload(id: Long): DownloadEntity? = downloadDao.getById(id)

    suspend fun getDownloadForMedia(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int? = null,
        episode: Int? = null
    ): DownloadEntity? = downloadDao.getByMedia(tmdbId, mediaType.name, season, episode)

    suspend fun getDownloadsForTmdb(tmdbId: Int, mediaType: MediaType): List<DownloadEntity> =
        downloadDao.getByTmdbId(tmdbId, mediaType.name)

    suspend fun isDownloaded(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int? = null,
        episode: Int? = null
    ): Boolean = downloadDao.isDownloaded(tmdbId, mediaType.name, season, episode) > 0

    suspend fun getTotalDownloadSize(): Long = downloadDao.getTotalDownloadSize() ?: 0L

    suspend fun enqueueDownload(
        tmdbId: Int,
        mediaType: MediaType,
        title: String,
        stream: StreamSource,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        posterPath: String? = null,
        backdropPath: String? = null,
        subtitleUrl: String? = null,
        subtitleLang: String? = null
    ): Long {
        val streamUrl = stream.url ?: throw IllegalArgumentException("Stream URL is null")

        // Check if already exists
        val existing = downloadDao.getByMedia(tmdbId, mediaType.name, season, episode)
        if (existing != null && existing.status == DownloadStatus.COMPLETED) {
            Log.d(TAG, "Already downloaded: $title")
            return existing.id
        }
        if (existing != null && (existing.status == DownloadStatus.QUEUED || existing.status == DownloadStatus.DOWNLOADING)) {
            Log.d(TAG, "Already queued/downloading: $title")
            return existing.id
        }

        // If previously failed, update and re-queue
        val entity = DownloadEntity(
            id = existing?.id ?: 0,
            tmdbId = tmdbId,
            mediaType = mediaType.name,
            season = season,
            episode = episode,
            title = title,
            episodeTitle = episodeTitle,
            posterPath = posterPath,
            backdropPath = backdropPath,
            streamUrl = streamUrl,
            addonId = stream.addonId.takeIf { it.isNotBlank() },
            addonName = stream.addonName.takeIf { it.isNotBlank() },
            quality = stream.quality.takeIf { it.isNotBlank() },
            status = DownloadStatus.QUEUED,
            progress = 0,
            downloadedBytes = 0,
            createdAt = System.currentTimeMillis(),
            subtitleUrl = subtitleUrl?.takeIf { it.isNotBlank() },
            subtitleLang = subtitleLang?.takeIf { it.isNotBlank() }
        )
        val downloadId = downloadDao.insert(entity)

        // Schedule WorkManager
        scheduleDownloadWork(downloadId, streamUrl, title, subtitleUrl)
        return downloadId
    }

    private suspend fun scheduleDownloadWork(downloadId: Long, streamUrl: String, title: String, subtitleUrl: String? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putLong(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
            .putString(DownloadWorker.KEY_STREAM_URL, streamUrl)
            .putString(DownloadWorker.KEY_TITLE, title)
            .apply { if (!subtitleUrl.isNullOrBlank()) putString(DownloadWorker.KEY_SUBTITLE_URL, subtitleUrl) }
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("download_$downloadId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Store worker ID
        downloadDao.updateWorkerId(downloadId, workRequest.id.toString())
    }

    suspend fun cancelDownload(downloadId: Long) {
        val download = downloadDao.getById(downloadId) ?: return
        WorkManager.getInstance(context).cancelUniqueWork("download_$downloadId")
        deletePartialFiles(downloadId)
        downloadDao.deleteById(downloadId)
    }

    suspend fun deleteDownload(downloadId: Long) {
        val download = downloadDao.getById(downloadId) ?: return

        // Cancel if still running
        WorkManager.getInstance(context).cancelUniqueWork("download_$downloadId")

        // Delete completed local video file
        try {
            val videoFile = download.localUri?.let { File(it) }
            if (videoFile?.exists() == true) videoFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: ${download.localUri}", e)
        }

        // Delete partial files (localUri is null for in-progress downloads)
        if (download.localUri == null) {
            deletePartialFiles(downloadId)
        }

        // Delete local subtitle file
        try {
            val subFile = download.subtitleLocalUri?.let { File(it) }
            if (subFile?.exists() == true) subFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete subtitle file: ${download.subtitleLocalUri}", e)
        }

        downloadDao.deleteById(downloadId)
    }

    private fun deletePartialFiles(downloadId: Long) {
        try {
            val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
            val prefix = "download_$downloadId"
            downloadsDir.listFiles { file -> file.name.startsWith(prefix) }?.forEach { file ->
                file.delete()
                Log.d(TAG, "Deleted partial file: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete partial files for download $downloadId", e)
        }
    }

    suspend fun retryDownload(downloadId: Long) {
        val download = downloadDao.getById(downloadId) ?: return
        downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED)
        scheduleDownloadWork(downloadId, download.streamUrl, download.title, download.subtitleUrl)
    }

    suspend fun pauseDownload(downloadId: Long) {
        val download = downloadDao.getById(downloadId) ?: return
        if (download.status != DownloadStatus.DOWNLOADING && download.status != DownloadStatus.QUEUED) return
        WorkManager.getInstance(context).cancelUniqueWork("download_$downloadId")
        downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
    }

    suspend fun resumeDownload(downloadId: Long) {
        val download = downloadDao.getById(downloadId) ?: return
        if (download.status != DownloadStatus.PAUSED) return
        downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED)
        scheduleDownloadWork(downloadId, download.streamUrl, download.title, download.subtitleUrl)
    }

    suspend fun getDownloadByLocalUri(localUri: String): DownloadEntity? =
        downloadDao.getByLocalUri(localUri)

    fun getDownloadsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
