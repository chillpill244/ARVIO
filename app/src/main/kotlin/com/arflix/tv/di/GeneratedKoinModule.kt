package com.arflix.tv.di

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.bind
import com.arflix.tv.ui.screens.series.SeriesViewModel
import com.arflix.tv.ui.screens.settings.SettingsViewModel
import com.arflix.tv.ui.screens.home.HomeViewModel
import com.arflix.tv.ui.screens.details.DetailsViewModel
import com.arflix.tv.ui.screens.details.IptvDetailsViewModel
import com.arflix.tv.ui.screens.tv.TvViewModel
import com.arflix.tv.ui.screens.search.SearchViewModel
import com.arflix.tv.ui.screens.profile.ProfileViewModel
import com.arflix.tv.ui.screens.movies.MoviesViewModel
import com.arflix.tv.ui.screens.collections.CollectionDetailsViewModel
import com.arflix.tv.ui.screens.downloads.DownloadsViewModel
import com.arflix.tv.ui.screens.login.LoginViewModel
import com.arflix.tv.ui.screens.watchlist.WatchlistViewModel
import com.arflix.tv.ui.screens.player.PlayerViewModel
import com.arflix.tv.ui.startup.StartupViewModel
import android.content.Context
import com.arflix.tv.util.authDataStore
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.traktDataStore
import com.arflix.tv.shared.repository.CloudSyncInvalidationBus as SharedCloudSyncInvalidationBus
import com.arflix.tv.shared.repository.CloudSyncScope as SharedCloudSyncScope
import com.arflix.tv.shared.repository.CloudSyncInvalidation as SharedCloudSyncInvalidation
import kotlinx.coroutines.flow.MutableSharedFlow
import com.arflix.tv.util.AnimeMapper
import com.arflix.tv.util.SoundManager
import com.arflix.tv.cast.CastManager
import com.arflix.tv.network.NetworkMonitor
import com.arflix.tv.updater.ApkDownloader
import com.arflix.tv.updater.UpdateStatusManager
import com.arflix.tv.updater.AppUpdateRepository
import com.arflix.tv.updater.UpdatePreferences
import com.arflix.tv.data.repository.CatalogRepository
import com.arflix.tv.data.repository.PlaybackTelemetryRepository
import com.arflix.tv.data.repository.HttpLocalScraperRuntime
import com.arflix.tv.shared.repository.TraktRepository
import com.arflix.tv.data.repository.TraktSyncServiceImpl
import com.arflix.tv.data.repository.DownloadsRepository
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.data.repository.LauncherContinueWatchingRepository
import com.arflix.tv.data.repository.SkipIntroRepository
import com.arflix.tv.data.repository.TvDeviceAuthRepository
import com.arflix.tv.shared.repository.WatchlistRepository
import com.arflix.tv.data.repository.AnimeScoreRepository
import com.arflix.tv.data.repository.CatalogDiscoveryRepository
import com.arflix.tv.data.repository.AppUsageAnalyticsRepository
import com.arflix.tv.data.repository.ProfileManagerImpl
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.CloudSyncRepository
import com.arflix.tv.data.repository.TraktOutboxRepository
import com.arflix.tv.data.repository.RealtimeSyncManager
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.data.repository.ProfileRepository
import com.arflix.tv.data.repository.HomeServerRepository
import com.arflix.tv.shared.repository.AuthRepository
import com.arflix.tv.data.repository.ProfileAvatarImageManager
import com.arflix.tv.data.repository.WatchHistoryRepository
import com.arflix.tv.data.repository.CloudSyncInvalidationBus
import com.arflix.tv.data.repository.CloudSyncCoordinator
import com.arflix.tv.data.api.InAppYouTubeExtractor

val generatedModule = module {
    viewModelOf(::SeriesViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::DetailsViewModel)
    viewModelOf(::IptvDetailsViewModel)
    viewModelOf(::TvViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::MoviesViewModel)
    viewModelOf(::CollectionDetailsViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::WatchlistViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::StartupViewModel)
    singleOf(::AnimeMapper)
    singleOf(::SoundManager)
    singleOf(::CastManager)
    singleOf(::NetworkMonitor)
    singleOf(::ApkDownloader)
    singleOf(::UpdateStatusManager)
    singleOf(::AppUpdateRepository)
    singleOf(::UpdatePreferences)
    singleOf(::CatalogRepository)
    singleOf(::PlaybackTelemetryRepository)
    singleOf(::HttpLocalScraperRuntime)
    single { TraktRepository(get<Context>().traktDataStore, get(), get(), get()) }
    singleOf(::TraktSyncServiceImpl)
    single<com.arflix.tv.shared.repository.TraktSyncService> { get<com.arflix.tv.data.repository.TraktSyncServiceImpl>() }
    singleOf(::DownloadsRepository)
    singleOf(::StreamRepository)
    singleOf(::LauncherContinueWatchingRepository)
    singleOf(::SkipIntroRepository)
    singleOf(::TvDeviceAuthRepository)
    single { WatchlistRepository(get<Context>().traktDataStore, get(), get(), get()) }
    singleOf(::AnimeScoreRepository)
    singleOf(::CatalogDiscoveryRepository)
    singleOf(::AppUsageAnalyticsRepository)
    singleOf(::ProfileManagerImpl)
    single<com.arflix.tv.shared.repository.ProfileManager> { get<com.arflix.tv.data.repository.ProfileManagerImpl>() }
    singleOf(::MediaRepository)
    singleOf(::CloudSyncRepository)
    singleOf(::TraktOutboxRepository)
    singleOf(::RealtimeSyncManager)
    singleOf(::IptvRepository)
    singleOf(::ProfileRepository)
    singleOf(::HomeServerRepository)
    single { AuthRepository(get<Context>().authDataStore, get<Context>().settingsDataStore) }
    singleOf(::ProfileAvatarImageManager)
    singleOf(::WatchHistoryRepository)
    singleOf(::CloudSyncInvalidationBus)
    single<SharedCloudSyncInvalidationBus> {
        val appBus = get<CloudSyncInvalidationBus>()
        object : SharedCloudSyncInvalidationBus {
            override val invalidationFlow = MutableSharedFlow<SharedCloudSyncInvalidation>()
            override fun markDirty(scope: SharedCloudSyncScope, profileId: String?, reason: String) {
                val appScope = when (scope) {
                    SharedCloudSyncScope.WATCHLIST -> com.arflix.tv.data.repository.CloudSyncScope.WATCHLIST
                    SharedCloudSyncScope.HISTORY -> com.arflix.tv.data.repository.CloudSyncScope.LOCAL_HISTORY
                    SharedCloudSyncScope.SETTINGS -> com.arflix.tv.data.repository.CloudSyncScope.PROFILE_SETTINGS
                }
                appBus.markDirty(appScope, profileId, reason)
            }
            override suspend fun <T> suppressDuringRemoteApply(block: suspend () -> T): T {
                return appBus.suppressDuringRemoteApply(block)
            }
        }
    }
    singleOf(::CloudSyncCoordinator)
    singleOf(::InAppYouTubeExtractor)
}
