package com.muvio.shared.di

import com.muvio.shared.network.StremioClient
import com.muvio.shared.network.TmdbClient
import com.muvio.shared.network.TraktClient
import com.muvio.shared.network.createHttpClient
import com.muvio.shared.repository.AddonRepository
import com.muvio.shared.repository.MediaRepository
import com.muvio.shared.repository.WatchHistoryRepository
import com.muvio.shared.storage.PlatformPreferences
import com.muvio.shared.viewmodel.DetailsViewModel
import com.muvio.shared.viewmodel.HomeViewModel
import com.muvio.shared.viewmodel.PlayerViewModel
import com.muvio.shared.viewmodel.SearchViewModel
import com.muvio.shared.viewmodel.SettingsViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Configuration injected by each app shell at startup. */
data class AppConfig(
    val tmdbApiKey: String = "",
    val traktClientId: String = "",
    val defaultProfileId: String = "default",
)

val networkModule = module {
    single { createHttpClient() }
}

val configModule = module {
    // AppConfig is registered by each app shell before this module runs:
    //   startKoin { modules(configModule(AppConfig(...)), ...) }
    // Provide a no-op default so tests don't require injection.
}

val storageModule = module {
    single(named("global")) { PlatformPreferences("muvio_global") }
    single(named("profile")) {
        val config = getOrNull<AppConfig>()
        val pid = config?.defaultProfileId ?: "default"
        PlatformPreferences("muvio_profile_$pid")
    }
}

val stremioModule = module {
    single { StremioClient(get()) }
}

val tmdbModule = module {
    single {
        val config = getOrNull<AppConfig>()
        TmdbClient(get(), config?.tmdbApiKey ?: "")
    }
}

val traktModule = module {
    single {
        val config = getOrNull<AppConfig>()
        TraktClient(get(), config?.traktClientId ?: "")
    }
}

val repositoryModule = module {
    single { MediaRepository(get()) }
    single {
        val config = getOrNull<AppConfig>()
        WatchHistoryRepository(get(named("profile")), config?.defaultProfileId ?: "default")
    }
    single {
        val config = getOrNull<AppConfig>()
        AddonRepository(get(), get(named("global")), config?.defaultProfileId ?: "default")
    }
}

val viewModelModule = module {
    factory { HomeViewModel(get()) }
    factory { SearchViewModel(get()) }
    factory { PlayerViewModel(get()) }
    factory { SettingsViewModel(get()) }
    factory { (tmdbId: Int, mediaTypeStr: String) ->
        val mediaType = com.muvio.shared.domain.MediaType.valueOf(mediaTypeStr)
        DetailsViewModel(tmdbId, mediaType, get(), get(), get())
    }
}

/** All modules to pass to startKoin { modules(...) } in each app shell. */
val sharedModules = listOf(
    networkModule,
    storageModule,
    stremioModule,
    tmdbModule,
    traktModule,
    repositoryModule,
    viewModelModule,
)
