package com.arflix.tv.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arflix.tv.data.repository.TraktRepository
import com.arflix.tv.data.repository.TraktSyncService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.first

/**
 * Background worker for syncing Trakt data periodically.
 * Syncs:
 * - Trakt watched state (movies/episodes)
 * - Trakt playback progress (Continue Watching)
 * - Pending outbox writes
 */
class TraktSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val traktRepository: com.arflix.tv.data.repository.TraktRepository by inject()
    private val syncService: com.arflix.tv.data.repository.TraktSyncService by inject()

    companion object {
        const val TAG = "TraktSyncWorker"
        const val WORK_NAME = "trakt_sync_worker"
        const val WORK_NAME_ON_OPEN = "trakt_sync_on_open"
        const val SYNC_INTERVAL_HOURS = 6L
        const val INPUT_SYNC_MODE = "sync_mode"
        const val SYNC_MODE_FULL = "full"
        const val SYNC_MODE_INCREMENTAL = "incremental"
    }

    override suspend fun doWork(): Result {
        // Check if user is authenticated
        if (!traktRepository.isAuthenticated.first()) {
            return Result.success()
        }

        val syncMode = inputData.getString(INPUT_SYNC_MODE) ?: SYNC_MODE_INCREMENTAL

        return try {
            when (syncMode) {
                SYNC_MODE_FULL -> syncService.performFullSync()
                else -> syncService.performIncrementalSync()
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    // TraktSyncService handles the sync pipeline.
}
