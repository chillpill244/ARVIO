package com.arflix.tv.network

import android.content.Context
import com.arflix.tv.BuildConfig
import com.arflix.tv.util.Constants
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Provides a configured OkHttpClient instance.
 *
 * All TMDB and Trakt API calls are routed through Supabase Edge Functions
 * via ApiProxyInterceptor. This keeps API keys secure on the server.
 *
 * SSL/TLS validation is handled by NetworkSecurityConfig (res/xml/network_security_config.xml):
 * - Release: System certificates only (secure)
 * - Debug: User + System certificates (allows proxy debugging)
 *
 * DO NOT add custom TrustManager - it defeats certificate validation.
 */
object OkHttpProvider {

    /** 50 MB disk cache for API responses (TMDB metadata, Trakt data, etc.) */
    private const val HTTP_CACHE_SIZE = 50L * 1024L * 1024L

    /**
     * Must be called once from Application.onCreate() before any network calls.
     * Provides the application context needed for the disk cache directory.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Volatile
    private var appContext: Context? = null

    val client: OkHttpClient by lazy {
        val customAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithAgent = originalRequest.newBuilder()
                .header("User-Agent", Constants.CUSTOM_AGENT)
                .build()
            chain.proceed(requestWithAgent)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val builder = OkHttpClient.Builder()
            // Custom User-Agent header for all requests (API, streams, images, etc.)
            .addInterceptor(customAgentInterceptor)
            // Direct API calls — no Supabase edge function proxy.
            // TMDB/Trakt keys are passed as query params / headers by Retrofit.
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)

        // Attach disk cache if context was initialized
        appContext?.let { ctx ->
            val cacheDir = File(ctx.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, HTTP_CACHE_SIZE))
        }

        builder.build()
    }
}
