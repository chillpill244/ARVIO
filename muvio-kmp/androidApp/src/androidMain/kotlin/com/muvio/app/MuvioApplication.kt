package com.muvio.app

import android.app.Application
import com.muvio.shared.di.AppConfig
import com.muvio.shared.di.sharedModules
import com.muvio.shared.storage.MuvioAndroidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MuvioApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Must happen before Koin starts so PlatformPreferences.android.kt can get Context
        MuvioAndroidContext.applicationContext = applicationContext

        val config = AppConfig(
            tmdbApiKey = BuildConfig.TMDB_API_KEY,
            traktClientId = BuildConfig.TRAKT_CLIENT_ID,
        )

        startKoin {
            modules(
                module { single { config } },
                *sharedModules.toTypedArray(),
            )
        }
    }
}
