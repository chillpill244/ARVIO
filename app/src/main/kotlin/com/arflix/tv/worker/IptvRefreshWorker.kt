package com.arflix.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.data.repository.IptvRefreshInterval
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class IptvRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val iptvRepository: IptvRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "IptvRefreshWorker"
        const val WORK_NAME = "iptv_refresh_worker"
    }

    override suspend fun doWork(): Result {
        val config = iptvRepository.observeConfig().first()
        if (config.m3uUrl.isBlank()) {
            return Result.success()
        }

        val refreshInterval = iptvRepository.observeRefreshInterval().first()
        if (refreshInterval == IptvRefreshInterval.DISABLED) {
            return Result.success()
        }

        return try {
            iptvRepository.loadSnapshot(
                forcePlaylistReload = true,
                forceEpgReload = false
            ) { }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
