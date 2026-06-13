package com.arflix.tv.util

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide [SimpleCache] holding offline HLS download segments.
 *
 * Never evicts — entries are only removed when the user deletes a download
 * ([com.arflix.tv.data.repository.DownloadsRepository.deleteDownload]). Lives in scoped
 * external storage so it is removed on uninstall, like the progressive downloads dir.
 * SimpleCache allows only one instance per directory, hence the singleton.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object DownloadCacheSingleton {
    @Volatile
    private var instance: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val cacheDir = (appContext.getExternalFilesDir("downloads_hls")
                    ?: File(appContext.filesDir, "downloads_hls")).apply { mkdirs() }
                SimpleCache(cacheDir, NoOpCacheEvictor(), StandaloneDatabaseProvider(appContext)).also {
                    instance = it
                }
            }
        }
    }
}
