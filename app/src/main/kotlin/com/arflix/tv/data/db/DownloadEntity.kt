package com.arflix.tv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["tmdbId", "mediaType", "season", "episode"], unique = true)
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val mediaType: String,        // "MOVIE" or "TV"
    val season: Int? = null,
    val episode: Int? = null,
    val title: String,
    val episodeTitle: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val localUri: String? = null,
    val streamUrl: String,
    val addonId: String? = null,
    val addonName: String? = null,
    val quality: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED,
    val progress: Int = 0,         // 0-100
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val workerId: String? = null,   // WorkManager work ID
    val subtitleUrl: String? = null,       // Remote URL to download subtitle from
    val subtitleLocalUri: String? = null,  // Local file path after subtitle download
    val subtitleLang: String? = null       // Language label (e.g. "English")
)

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}
