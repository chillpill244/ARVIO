package com.arflix.tv.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND season IS :season AND episode IS :episode LIMIT 1")
    suspend fun getByMedia(tmdbId: Int, mediaType: String, season: Int?, episode: Int?): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND mediaType = :mediaType ORDER BY season ASC, episode ASC")
    suspend fun getByTmdbId(tmdbId: Int, mediaType: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND mediaType = :mediaType ORDER BY season ASC, episode ASC")
    fun observeByTmdbId(tmdbId: Int, mediaType: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<DownloadEntity>

    @Query("UPDATE downloads SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Int, downloadedBytes: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String? = null)

    @Query("UPDATE downloads SET status = :status, localUri = :localUri, fileSize = :fileSize, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, status: String, localUri: String, fileSize: Long, completedAt: Long)

    @Query("UPDATE downloads SET workerId = :workerId WHERE id = :id")
    suspend fun updateWorkerId(id: Long, workerId: String)

    @Query("SELECT * FROM downloads WHERE mediaType = :mediaType AND status = :status ORDER BY createdAt DESC")
    suspend fun getByMediaTypeAndStatus(mediaType: String, status: String): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND season = :season AND episode = :episode AND status = 'COMPLETED'")
    suspend fun isDownloaded(tmdbId: Int, mediaType: String, season: Int?, episode: Int?): Int

    @Query("SELECT SUM(fileSize) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getTotalDownloadSize(): Long?

    @Query("UPDATE downloads SET subtitleLocalUri = :uri WHERE id = :id")
    suspend fun updateSubtitleUri(id: Long, uri: String)

    @Query("SELECT * FROM downloads WHERE localUri = :localUri LIMIT 1")
    suspend fun getByLocalUri(localUri: String): DownloadEntity?
}
