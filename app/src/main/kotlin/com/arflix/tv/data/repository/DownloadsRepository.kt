package com.arflix.tv.data.repository
import com.arflix.tv.shared.repository.ProfileManager
import com.arflix.tv.shared.repository.AuthRepository


import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.arflix.tv.data.db.DownloadDao
import com.arflix.tv.data.db.DownloadEntity
import com.arflix.tv.data.db.DownloadStatus
import com.arflix.tv.data.db.DownloadType
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.util.HlsDownloadUtil
import com.arflix.tv.worker.DownloadWorker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadsRepository constructor(
    private val dao: DownloadDao,
    private val workManager: WorkManager,
    private val context: Context
) {

    fun observeAllDownloads(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeDownloadsForMedia(tmdbId: Int): Flow<List<DownloadEntity>> =
        dao.observeByTmdbId(tmdbId)

    suspend fun getDownloadForEpisode(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): DownloadEntity? = dao.findDownload(tmdbId, mediaType, season, episode)

    suspend fun getDownloadByLocalUri(localUri: String): DownloadEntity? =
        dao.findByLocalUri(localUri)

    suspend fun enqueueDownload(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        title: String,
        episodeTitle: String?,
        posterPath: String?,
        backdropPath: String?,
        streamUrl: String,
        addonId: String,
        addonName: String,
        quality: String,
        subtitleUrl: String?,
        subtitleLang: String?,
        headers: Map<String, String>? = null,
        downloadType: String = DownloadType.FILE.name,
        streamKeys: String? = null
    ) {
        val existing = dao.findDownload(tmdbId, mediaType, season, episode)
        if (existing != null) {
            when (existing.status) {
                DownloadStatus.COMPLETED.name -> return
                DownloadStatus.QUEUED.name,
                DownloadStatus.DOWNLOADING.name -> return
                DownloadStatus.FAILED.name -> {
                    retryDownload(existing.id)
                    return
                }
                DownloadStatus.PAUSED.name -> {
                    resumeDownload(existing.id)
                    return
                }
            }
        }

        val headersJson = headers?.takeIf { it.isNotEmpty() }?.let { Gson().toJson(it) }
        val entity = DownloadEntity(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            episodeTitle = episodeTitle,
            posterPath = posterPath,
            backdropPath = backdropPath,
            streamUrl = streamUrl,
            addonId = addonId,
            addonName = addonName,
            quality = quality,
            subtitleUrl = subtitleUrl,
            subtitleLang = subtitleLang,
            status = DownloadStatus.QUEUED.name,
            headers = headersJson,
            downloadType = downloadType,
            streamKeys = streamKeys
        )
        val id = dao.insert(entity)
        scheduleWork(id, streamUrl, subtitleUrl)
    }

    suspend fun pauseDownload(id: Long) {
        workManager.cancelAllWorkByTag(workTag(id))
        dao.updateStatus(id, DownloadStatus.PAUSED.name)
        // Keep the partial file — resume will append from where we left off via Range header.
    }

    suspend fun resumeDownload(id: Long) {
        val entity = dao.getById(id) ?: return
        dao.updateStatus(id, DownloadStatus.QUEUED.name)
        scheduleWork(id, entity.streamUrl, entity.subtitleUrl)
    }

    suspend fun cancelDownload(id: Long) {
        val entity = dao.getById(id) ?: return
        workManager.cancelAllWorkByTag(workTag(id))
        deleteVideoData(entity)
        dao.updateStatus(id, DownloadStatus.FAILED.name)
    }

    suspend fun deleteDownload(id: Long) {
        val entity = dao.getById(id) ?: return
        workManager.cancelAllWorkByTag(workTag(id))
        deleteVideoData(entity)
        entity.subtitleLocalUri?.let { File(it) }?.takeIf { it.exists() }?.delete()
        dao.delete(entity)
    }

    /**
     * Removes a download's video data: local file for FILE downloads, cached segments for HLS.
     * HLS removal works offline — the segment list is enumerated from the cached playlists.
     */
    private suspend fun deleteVideoData(entity: DownloadEntity) {
        if (entity.downloadType == DownloadType.HLS.name) {
            withContext(Dispatchers.IO) {
                runCatching {
                    HlsDownloadUtil.createDownloader(
                        context = context,
                        url = entity.streamUrl,
                        streamKeysJson = entity.streamKeys,
                        userAgent = OkHttpProvider.userAgent,
                        headers = parseHeaders(entity.headers),
                        executor = Runnable::run
                    ).remove()
                }
            }
        } else {
            entity.localUri?.let { File(it) }?.takeIf { it.exists() }?.delete()
        }
    }

    private fun parseHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson<Map<String, String>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    suspend fun retryDownload(id: Long) {
        val entity = dao.getById(id) ?: return
        dao.updateStatus(id, DownloadStatus.QUEUED.name)
        scheduleWork(id, entity.streamUrl, entity.subtitleUrl)
    }

    suspend fun deleteAllForSeries(tmdbId: Int) {
        dao.getAllByTmdbId(tmdbId).forEach { deleteDownload(it.id) }
    }

    fun downloadsDir(): File =
        context.getExternalFilesDir("downloads") ?: context.filesDir.resolve("downloads")

    suspend fun updateProgressInternal(id: Long, progress: Int, downloadedBytes: Long) {
        dao.updateProgress(id, progress, downloadedBytes)
    }

    suspend fun markCompletedInternal(
        id: Long,
        localUri: String,
        fileSize: Long,
        subtitleLocalUri: String?,
        subtitleLang: String?
    ) {
        dao.markCompleted(id, DownloadStatus.COMPLETED.name, localUri, fileSize)
        if (subtitleLocalUri != null && subtitleLang != null) {
            dao.updateSubtitle(id, subtitleLocalUri, subtitleLang)
        }
    }

    suspend fun markFailedInternal(id: Long) {
        dao.updateStatus(id, DownloadStatus.FAILED.name)
    }

    suspend fun updateStatusInternal(id: Long, status: DownloadStatus) {
        dao.updateStatus(id, status.name)
    }

    private suspend fun scheduleWork(id: Long, streamUrl: String, subtitleUrl: String?) {
        val entity = dao.getById(id)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(workTag(id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_DOWNLOAD_ID to id,
                    DownloadWorker.KEY_STREAM_URL to streamUrl,
                    DownloadWorker.KEY_SUBTITLE_URL to subtitleUrl,
                    DownloadWorker.KEY_USER_AGENT to OkHttpProvider.userAgent,
                    DownloadWorker.KEY_HEADERS to (entity?.headers ?: ""),
                    DownloadWorker.KEY_TITLE to (entity?.title ?: ""),
                    DownloadWorker.KEY_SEASON to (entity?.season ?: -1),
                    DownloadWorker.KEY_EPISODE to (entity?.episode ?: -1),
                    DownloadWorker.KEY_DOWNLOAD_TYPE to (entity?.downloadType ?: DownloadType.FILE.name),
                    DownloadWorker.KEY_STREAM_KEYS to (entity?.streamKeys ?: "")
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            workName(id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun workTag(id: Long) = "download_$id"
    private fun workName(id: Long) = "download_$id"
}
