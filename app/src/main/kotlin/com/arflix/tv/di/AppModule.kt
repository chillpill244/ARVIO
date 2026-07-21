package com.arflix.tv.di

import com.arflix.tv.data.api.AniSkipApi
import com.arflix.tv.data.api.ArmApi
import com.arflix.tv.data.api.IntroDbApi
import com.arflix.tv.data.api.StreamApi
import com.arflix.tv.data.api.SupabaseApi
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TraktApi
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.util.Constants
import okhttp3.OkHttpClient
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.mediaCategoryPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import androidx.work.WorkManager
import org.koin.dsl.module
import com.arflix.tv.data.repository.AndroidPreferenceStore
import com.arflix.tv.data.repository.PlatformEnvironment
import com.arflix.tv.data.repository.AndroidPlatformEnvironment
import com.arflix.tv.data.repository.PreferenceStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest

val appModule = module {
    single<WorkManager> { WorkManager.getInstance(androidContext()) }

    single<OkHttpClient> { OkHttpProvider.client }
    single<HttpClient> { OkHttpProvider.ktorClient }
    
    single<TmdbApi> {
        val client = get<HttpClient>().config {
            defaultRequest { url(Constants.TMDB_BASE_URL) }
        }
        TmdbApi(client)
    }
    
    single<TraktApi> {
        val client = get<HttpClient>().config {
            defaultRequest { url(Constants.TRAKT_API_URL) }
        }
        TraktApi(client)
    }
    
    single<SupabaseApi> {
        // Supabase requires a no-cache client in Retrofit setup, but for Ktor we can just use the standard one
        // and optionally disable caching per request if needed. For now, just use the standard one.
        val client = get<HttpClient>().config {
            defaultRequest { url(Constants.SUPABASE_URL + "/") }
        }
        SupabaseApi(client)
    }
    
    single<StreamApi> {
        val client = get<HttpClient>().config {
            defaultRequest { url("https://api.themoviedb.org/") }
        }
        StreamApi(client)
    }

    single<IntroDbApi> { 
        val client = get<HttpClient>().config {
            defaultRequest { url("https://api.introdb.app/") }
        }
        IntroDbApi(client)
    }

    single<AniSkipApi> { 
        val client = get<HttpClient>().config {
            defaultRequest { url("https://api.aniskip.com/v2/") }
        }
        AniSkipApi(client)
    }

    single<ArmApi> { 
        val client = get<HttpClient>().config {
            defaultRequest { url("https://arm.haglund.dev/api/v2/") }
        }
        ArmApi(client)
    }

    single<com.arflix.tv.data.api.JikanApi> { 
        val client = get<HttpClient>().config {
            defaultRequest { url("https://api.jikan.moe/v4/") }
        }
        com.arflix.tv.data.api.JikanApi(client)
    }

    single<PreferenceStore> {
        val context = androidContext()
        AndroidPreferenceStore(
            context = context,
            settings = context.settingsDataStore,
            mediaCategory = context.mediaCategoryPreferences
        )
    }

    single<PlatformEnvironment> { AndroidPlatformEnvironment(androidContext()) }
}
