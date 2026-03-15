package com.arflix.tv.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import com.arflix.tv.data.model.IptvSnapshot
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.util.Constants
import com.arflix.tv.util.settingsDataStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.xml.XMLConstants
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.lang.reflect.Type
import java.security.KeyStore
import java.security.MessageDigest

data class IptvConfig(
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = ""
)

data class IptvLoadProgress(
    val message: String,
    val percent: Int? = null
)

data class IptvCloudProfileState(
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val favoriteGroups: List<String> = emptyList(),
    val favoriteChannels: List<String> = emptyList()
)

enum class IptvRefreshInterval(val hours: Long, val displayName: String) {
    DISABLED(0, "Disabled"),
    EVERY_12_HOURS(12, "Every 12 hours"),
    DAILY(24, "Every 24 hours"),
    EVERY_48_HOURS(48, "Every 48 hours");

    companion object {
        fun fromHours(hours: Long): IptvRefreshInterval {
            return entries.find { it.hours == hours } ?: DISABLED
        }
    }
}

@Singleton
class IptvRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val profileManager: ProfileManager
) {
    private val gson = Gson()
    private val loadMutex = Mutex()
    private val xtreamDataMutex = Mutex()
    private val xtreamSeriesEpisodeCacheMutex = Mutex()
    private val xtreamSeriesEpisodeInFlightMutex = Mutex()
    private val maxSeriesEpisodeCacheEntries = 8

    @Volatile
    private var cachedChannels: List<IptvChannel> = emptyList()

    @Volatile
    private var cachedNowNext: ConcurrentHashMap<String, IptvNowNext> = ConcurrentHashMap()

    @Volatile
    private var cachedPlaylistAt: Long = 0L

    @Volatile
    private var cachedEpgAt: Long = 0L

    @Volatile
    private var preferredDerivedEpgUrl: String? = null
    @Volatile
    private var discoveredM3uEpgUrl: String? = null
    @Volatile
    private var cacheOwnerProfileId: String? = null
    @Volatile
    private var cacheOwnerConfigSig: String? = null
    @Volatile
    private var xtreamVodCacheKey: String? = null
    @Volatile
    private var xtreamVodLoadedAtMs: Long = 0L
    @Volatile
    private var xtreamSeriesLoadedAtMs: Long = 0L
    @Volatile
    private var cachedXtreamVodStreams: List<XtreamVodStream> = emptyList()
    @Volatile
    private var cachedXtreamSeries: List<XtreamSeriesItem> = emptyList()
    @Volatile
    private var cachedXtreamSeriesEpisodes: Map<Int, List<XtreamSeriesEpisode>> = emptyMap()
    @Volatile
    private var cachedVodCategories: List<XtreamVodCategory> = emptyList()
    @Volatile
    private var cachedSeriesCategories: List<XtreamSeriesCategory> = emptyList()
    @Volatile
    private var xtreamSeriesEpisodeInFlight: Map<Int, Deferred<List<XtreamSeriesEpisode>>> = emptyMap()
    @Volatile
    private var cachedVodIndex: VodCatalogIndex? = null
    private val seriesResolver by lazy { IptvSeriesResolverService() }
    private val bracketContentRegex = Regex("""\[[^\]]*]""")
    private val parenContentRegex = Regex("""\([^\)]*\)""")
    private val yearParenRegex = Regex("""\((19|20)\d{2}\)""")
    private val seasonTokenRegex = Regex("""\b(s|season)\s*\d{1,2}\b""", RegexOption.IGNORE_CASE)
    private val episodeTokenRegex = Regex("""\b(e|ep|episode)\s*\d{1,3}\b""", RegexOption.IGNORE_CASE)
    private val releaseTagRegex = Regex(
        """\b(2160p|1080p|720p|480p|4k|uhd|fhd|hdr|dv|dovi|hevc|x265|x264|h264|remux|bluray|bdrip|webrip|web[- ]?dl|proper|repack|multi|dubbed|dual[- ]?audio)\b""",
        RegexOption.IGNORE_CASE
    )
    private val nonAlphaNumRegex = Regex("[^a-z0-9]+")
    private val multiSpaceRegex = Regex("\\s+")

    private val staleAfterMs = 24 * 60 * 60_000L
    private val playlistCacheMs = staleAfterMs
    private val epgCacheMs = staleAfterMs
    private val epgEmptyRetryMs = 30_000L
    private val xtreamVodCacheMs = 6 * 60 * 60_000L
    private val iptvHttpClient: OkHttpClient by lazy {
        // Used for full playlist/EPG loading – generous timeouts for large Xtream EPG feeds.
        okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
    private val xtreamLookupHttpClient: OkHttpClient by lazy {
        // Fast-fail client for VOD/source lookups - must be quick for instant playback
        okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private data class IptvCachePayload(
        val channels: List<IptvChannel> = emptyList(),
        val nowNext: Map<String, IptvNowNext> = emptyMap(),
        val loadedAtEpochMs: Long = 0L,
        val configSignature: String = ""
    )

    fun observeConfig(): Flow<IptvConfig> =
        profileManager.activeProfileId.combine(context.settingsDataStore.data) { _, prefs ->
            IptvConfig(
                m3uUrl = decryptConfigValue(prefs[m3uUrlKey()].orEmpty()),
                epgUrl = decryptConfigValue(prefs[epgUrlKey()].orEmpty()),
                xtreamUsername = decryptConfigValue(prefs[xtreamUsernameKey()].orEmpty()),
                xtreamPassword = decryptConfigValue(prefs[xtreamPasswordKey()].orEmpty())
            )
        }

    fun observeFavoriteGroups(): Flow<List<String>> =
        profileManager.activeProfileId.combine(context.settingsDataStore.data) { _, prefs ->
            decodeFavoriteGroups(prefs)
        }

    fun observeFavoriteChannels(): Flow<List<String>> =
        profileManager.activeProfileId.combine(context.settingsDataStore.data) { _, prefs ->
            decodeFavoriteChannels(prefs)
        }

    fun observeRefreshInterval(): Flow<IptvRefreshInterval> =
        profileManager.activeProfileId.combine(context.settingsDataStore.data) { _, prefs ->
            // Default to 12 hours if not set (was DISABLED)
            IptvRefreshInterval.fromHours(prefs[refreshIntervalKey()] ?: 12L)
        }

    fun observeLastRefreshTime(): Flow<Long?> =
        profileManager.activeProfileId.combine(context.settingsDataStore.data) { _, prefs ->
            prefs[lastRefreshTimeKey()]
        }

    suspend fun setRefreshInterval(interval: IptvRefreshInterval) {
        context.settingsDataStore.edit { prefs ->
            prefs[refreshIntervalKey()] = interval.hours
        }
    }

    suspend fun setLastRefreshTime(timestampMs: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastRefreshTimeKey()] = timestampMs
        }
    }

    suspend fun saveConfig(m3uUrl: String, epgUrl: String, xtreamUsername: String = "", xtreamPassword: String = "") {
        // If explicit Xtream credentials provided, store them separately and use just the host
        val (normalizedM3u, storedUsername, storedPassword) = if (xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()) {
            // Extract just the host from m3uUrl (might be full URL or host)
            val hostOnly = m3uUrl.trim()
                .let { url ->
                    // Remove protocol if present
                    if (url.contains("://")) {
                        url.substringAfter("://")
                            .substringBefore("/")
                    } else {
                        url
                    }
                }
            Triple(hostOnly, xtreamUsername.trim(), xtreamPassword.trim())
        } else {
            // No explicit credentials - normalize as before (which may extract from URL)
            Triple(normalizeIptvInput(m3uUrl), "", "")
        }
        
        val normalizedEpg = if (xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()) {
            epgUrl.trim()  // Use EPG as-is if explicit credentials provided
        } else {
            normalizeEpgInput(epgUrl)
        }
        
        context.settingsDataStore.edit { prefs ->
            prefs[m3uUrlKey()] = encryptConfigValue(normalizedM3u)
            prefs[epgUrlKey()] = encryptConfigValue(normalizedEpg)
            prefs[xtreamUsernameKey()] = encryptConfigValue(storedUsername)
            prefs[xtreamPasswordKey()] = encryptConfigValue(storedPassword)
        }
        invalidateCache()
    }

    /**
     * Accept common Xtream Codes inputs and convert to a canonical M3U URL.
     *
     * Supported inputs:
     * - Full m3u/get.php URL: https://host/get.php?username=U&password=P&type=m3u_plus&output=ts
     * - Space-separated: https://host:port U P
     * - Line-separated: host\nuser\npass
     * - Prefix forms: xtream://host user pass (also xstream://)
     */
    private fun normalizeIptvInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        // Handle explicit Xtream triplets first (works for hosts with or without scheme).
        extractXtreamTriplet(trimmed)?.let { (host, user, pass) ->
            normalizeXtreamHost(host)?.let { base -> return buildXtreamM3uUrl(base, user, pass) }
        }

        // Already a URL.
        if (trimmed.contains("://")) {
            // If this is an Xtream get.php URL, normalize type/output to a sensible default.
            val parsed = trimmed.toHttpUrlOrNull()
            if (parsed != null && (
                    parsed.encodedPath.endsWith("/get.php") ||
                        parsed.encodedPath.endsWith("/player_api.php")
                    )
            ) {
                val username = parsed.queryParameter("username")?.trim()?.ifBlank { null }
                    ?: parsed.queryParameter("user")?.trim()?.ifBlank { null }
                    ?: parsed.queryParameter("uname")?.trim()?.ifBlank { null }
                    ?: ""
                val password = parsed.queryParameter("password")?.trim()?.ifBlank { null }
                    ?: parsed.queryParameter("pass")?.trim()?.ifBlank { null }
                    ?: parsed.queryParameter("pwd")?.trim()?.ifBlank { null }
                    ?: ""
                if (username.isNotBlank() && password.isNotBlank()) {
                    val base = parsed.toXtreamBaseUrl()
                    return buildXtreamM3uUrl(base, username, password)
                }
            }
            return trimmed
        }

        // Multi-line: host\nuser\npass.
        val partsByLine = trimmed
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partsByLine.size >= 3) {
            val host = partsByLine[0]
            val user = partsByLine[1]
            val pass = partsByLine[2]
            normalizeXtreamHost(host)?.let { base -> return buildXtreamM3uUrl(base, user, pass) }
        }

        // Space-separated: host user pass.
        val partsBySpace = trimmed
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partsBySpace.size >= 3) {
            val host = partsBySpace[0]
            val user = partsBySpace[1]
            val pass = partsBySpace[2]
            normalizeXtreamHost(host)?.let { base -> return buildXtreamM3uUrl(base, user, pass) }
        }

        return trimmed
    }

    /**
     * Accept Xtream credentials in the EPG field too.
     *
     * Supported:
     * - Full xmltv.php URL
     * - Full get.php URL (auto-converts to xmltv.php)
     * - host user pass (space-separated)
     * - host\\nuser\\npass (line-separated)
     * - xtream://host user pass
     */
    private fun normalizeEpgInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        // Handle explicit Xtream triplets first (works for hosts with or without scheme).
        extractXtreamTriplet(trimmed)?.let { (host, user, pass) ->
            normalizeXtreamHost(host)?.let { base -> return buildXtreamEpgUrl(base, user, pass) }
        }

        if (trimmed.contains("://")) {
            val parsed = trimmed.toHttpUrlOrNull()
            if (parsed != null) {
                val isXtreamPath = parsed.encodedPath.endsWith("/xmltv.php") ||
                    parsed.encodedPath.endsWith("/get.php") ||
                    parsed.encodedPath.endsWith("/player_api.php")
                if (isXtreamPath) {
                    val username = parsed.queryParameter("username")?.trim()?.ifBlank { null }
                        ?: parsed.queryParameter("user")?.trim()?.ifBlank { null }
                        ?: parsed.queryParameter("uname")?.trim()?.ifBlank { null }
                        ?: ""
                    val password = parsed.queryParameter("password")?.trim()?.ifBlank { null }
                        ?: parsed.queryParameter("pass")?.trim()?.ifBlank { null }
                        ?: parsed.queryParameter("pwd")?.trim()?.ifBlank { null }
                        ?: ""
                    if (username.isNotBlank() && password.isNotBlank()) {
                        val base = parsed.toXtreamBaseUrl()
                        return buildXtreamEpgUrl(base, username, password)
                    }
                }
            }
            return trimmed
        }

        val partsByLine = trimmed
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partsByLine.size >= 3) {
            val host = partsByLine[0]
            val user = partsByLine[1]
            val pass = partsByLine[2]
            normalizeXtreamHost(host)?.let { base -> return buildXtreamEpgUrl(base, user, pass) }
        }

        val partsBySpace = trimmed
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partsBySpace.size >= 3) {
            val host = partsBySpace[0]
            val user = partsBySpace[1]
            val pass = partsBySpace[2]
            normalizeXtreamHost(host)?.let { base -> return buildXtreamEpgUrl(base, user, pass) }
        }

        return trimmed
    }

    private data class XtreamTriplet(
        val host: String,
        val username: String,
        val password: String
    )

    private fun extractXtreamTriplet(raw: String): XtreamTriplet? {
        // Multi-line: host\nuser\npass.
        val partsByLine = raw
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partsByLine.size >= 3) {
            return XtreamTriplet(
                host = partsByLine[0],
                username = partsByLine[1],
                password = partsByLine[2]
            )
        }

        // Space-separated: host user pass.
        val partsBySpace = raw
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (partsBySpace.size >= 3) {
            return XtreamTriplet(
                host = partsBySpace[0],
                username = partsBySpace[1],
                password = partsBySpace[2]
            )
        }

        return null
    }

    private fun okhttp3.HttpUrl.toXtreamBaseUrl(): String {
        val raw = toString().substringBefore('?').trimEnd('/')
        return raw
            .removeSuffix("/get.php")
            .removeSuffix("/xmltv.php")
            .removeSuffix("/player_api.php")
            .trimEnd('/')
    }

    private fun normalizeXtreamHost(host: String): String? {
        val h = host.trim().removeSuffix("/")
        if (h.isBlank()) return null

        val cleaned = h
            .removePrefix("xtream://")
            .removePrefix("xstream://")
            .removePrefix("xtreamcodes://")
            .removePrefix("xc://")
            .let {
                when {
                    it.startsWith("http:/", ignoreCase = true) && !it.startsWith("http://", ignoreCase = true) ->
                        "http://${it.removePrefix("http:/").removePrefix("/")}"
                    it.startsWith("https:/", ignoreCase = true) && !it.startsWith("https://", ignoreCase = true) ->
                        "https://${it.removePrefix("https:/").removePrefix("/")}"
                    else -> it
                }
            }

        // Add scheme if missing.
        return if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
            cleaned.removeSuffix("/")
        } else {
            // Default to http (most providers use http).
            "http://${cleaned.removeSuffix("/")}"
        }
    }

    private fun buildXtreamM3uUrl(baseUrl: String, username: String, password: String): String {
        val safeBase = baseUrl.trim().trimEnd('/')
        val u = username.trim()
        val p = password.trim()
        return "$safeBase/get.php?username=$u&password=$p&type=m3u_plus&output=ts"
    }

    private fun buildXtreamEpgUrl(baseUrl: String, username: String, password: String): String {
        val safeBase = baseUrl.trim().trimEnd('/')
        val u = username.trim()
        val p = password.trim()
        return "$safeBase/xmltv.php?username=$u&password=$p"
    }

    suspend fun clearConfig() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(m3uUrlKey())
            prefs.remove(epgUrlKey())
            prefs.remove(xtreamUsernameKey())
            prefs.remove(xtreamPasswordKey())
            prefs.remove(favoriteGroupsKey())
            prefs.remove(favoriteChannelsKey())
        }
        invalidateCache()
        runCatching { cacheFile().delete() }
    }

    suspend fun importCloudConfig(
        m3uUrl: String,
        epgUrl: String,
        favoriteGroups: List<String>,
        favoriteChannels: List<String> = emptyList()
    ) {
        context.settingsDataStore.edit { prefs ->
            if (m3uUrl.isBlank()) {
                prefs.remove(m3uUrlKey())
            } else {
                prefs[m3uUrlKey()] = encryptConfigValue(m3uUrl)
            }
            if (epgUrl.isBlank()) {
                prefs.remove(epgUrlKey())
            } else {
                prefs[epgUrlKey()] = encryptConfigValue(epgUrl)
            }
            val cleanedFavorites = favoriteGroups
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (cleanedFavorites.isEmpty()) {
                prefs.remove(favoriteGroupsKey())
            } else {
                prefs[favoriteGroupsKey()] = gson.toJson(cleanedFavorites)
            }

            val cleanedFavoriteChannels = favoriteChannels
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (cleanedFavoriteChannels.isEmpty()) {
                prefs.remove(favoriteChannelsKey())
            } else {
                prefs[favoriteChannelsKey()] = gson.toJson(cleanedFavoriteChannels)
            }
        }
        invalidateCache()
    }

    suspend fun toggleFavoriteGroup(groupName: String) {
        val trimmed = groupName.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val existing = decodeFavoriteGroups(prefs).toMutableList()
            if (existing.contains(trimmed)) {
                existing.remove(trimmed)
            } else {
                existing.remove(trimmed)
                existing.add(0, trimmed) // newest favorite first
            }
            prefs[favoriteGroupsKey()] = gson.toJson(existing)
        }
    }

    suspend fun toggleFavoriteChannel(channelId: String) {
        val trimmed = channelId.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val existing = decodeFavoriteChannels(prefs).toMutableList()
            if (existing.contains(trimmed)) {
                existing.remove(trimmed)
            } else {
                existing.remove(trimmed)
                existing.add(0, trimmed)
            }
            prefs[favoriteChannelsKey()] = gson.toJson(existing)
        }
    }

    suspend fun loadSnapshot(
        forcePlaylistReload: Boolean = false,
        forceEpgReload: Boolean = false,
        onProgress: (IptvLoadProgress) -> Unit = {}
    ): IptvSnapshot {
        return withContext(Dispatchers.IO) {
            loadMutex.withLock {
            cleanupStaleEpgTempFiles()
            onProgress(IptvLoadProgress("Starting IPTV load...", 2))
            val now = System.currentTimeMillis()
            val config = observeConfig().first()
            val profileId = profileManager.getProfileIdSync()
            ensureCacheOwnership(profileId, config)
            cleanupIptvCacheDirectory()
            if (config.m3uUrl.isBlank()) {
                return@withContext IptvSnapshot(
                    channels = emptyList(),
                    grouped = emptyMap(),
                    nowNext = emptyMap(),
                    favoriteGroups = observeFavoriteGroups().first(),
                    favoriteChannels = observeFavoriteChannels().first(),
                    loadedAt = Instant.now()
                )
            }

            val cachedFromDisk = if (cachedChannels.isEmpty()) readCache(config) else null
            if (cachedFromDisk != null) {
                cachedChannels = cachedFromDisk.channels
                cachedNowNext = ConcurrentHashMap(cachedFromDisk.nowNext)
                cachedPlaylistAt = cachedFromDisk.loadedAtEpochMs
                cachedEpgAt = cachedFromDisk.loadedAtEpochMs
            }

            val channels = if (!forcePlaylistReload && cachedChannels.isNotEmpty()) {
                val isFresh = now - cachedPlaylistAt < playlistCacheMs
                onProgress(
                    IptvLoadProgress(
                        if (isFresh) {
                            "Using cached playlist (${cachedChannels.size} channels)"
                        } else {
                            "Using cached playlist (${cachedChannels.size} channels, stale)"
                        },
                        80
                    )
                )
                cachedChannels
            } else {
                fetchAndParseM3uWithRetries(buildFetchM3uUrl(config), onProgress).also {
                    cachedChannels = it
                    cachedPlaylistAt = System.currentTimeMillis()
                }
            }

            val epgCandidates = resolveEpgCandidates(config)
            var epgUpdated = false
            val cachedHasPrograms = hasAnyProgramData(cachedNowNext)
            val shouldUseCachedEpg = !forceEpgReload && (
                cachedHasPrograms ||
                    (!cachedHasPrograms && now - cachedEpgAt < epgEmptyRetryMs)
                )
            var epgFailureMessage: String? = null

            // Check if this is an Xtream provider (can use fast short EPG API)
            val xtreamCreds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
            val hasXtreamChannels = channels.any { it.xtreamStreamId != null || it.id.startsWith("xtream:") }
            System.err.println("[EPG] loadSnapshot: forceEpgReload=$forceEpgReload shouldUseCachedEpg=$shouldUseCachedEpg cachedHasPrograms=$cachedHasPrograms xtreamCreds=${xtreamCreds != null} hasXtreamChannels=$hasXtreamChannels epgCandidates=${epgCandidates.size}")
            val nowNext = if (epgCandidates.isEmpty() && xtreamCreds == null) {
                onProgress(IptvLoadProgress("No EPG URL configured", 90))
                System.err.println("[EPG] No EPG URL and no Xtream creds - skipping EPG")
                emptyMap()
            } else if (shouldUseCachedEpg) {
                onProgress(IptvLoadProgress("Using cached EPG", 92))
                System.err.println("[EPG] Using cached EPG (${cachedNowNext.size} channels, age=${(now - cachedEpgAt)/1000}s)")
                cachedNowNext
            } else {
                var resolvedNowNext: Map<String, IptvNowNext> = emptyMap()
                var resolved = false

                // Collect EPG from multiple sources and merge them all.
                // Short EPG is fast (~10s) but only covers channels that have data.
                // XMLTV is slow (~60s) but comprehensive. Both run, results are merged.
                var shortEpgResult: Map<String, IptvNowNext>? = null

                // ── Fast path: Xtream short EPG API ──
                if (xtreamCreds != null && hasXtreamChannels) {
                    System.err.println("[EPG] Attempting Xtream short EPG (baseUrl=${xtreamCreds.baseUrl})")
                    val shortEpgAttempt = runCatching {
                        fetchXtreamShortEpg(xtreamCreds, channels, onProgress)
                    }
                    if (shortEpgAttempt.isSuccess) {
                        val parsed = shortEpgAttempt.getOrNull()
                        val parsedHasData = parsed != null && hasAnyProgramData(parsed)
                        System.err.println("[EPG] Xtream short EPG result: ${parsed?.size ?: 0} channels, hasData=$parsedHasData")
                        if (parsed != null && parsedHasData) {
                            shortEpgResult = parsed
                            // Provide immediate results: merge short EPG with cached data (no stale removal)
                            cachedNowNext.putAll(parsed) // Short EPG data takes priority (fresher)
                            resolvedNowNext = cachedNowNext
                            cachedEpgAt = System.currentTimeMillis()
                            epgUpdated = true
                            resolved = true
                            System.err.println("[EPG] Xtream short EPG SUCCESS: ${parsed.size} fresh, ${cachedNowNext.size} total cached")
                        }
                    } else {
                        System.err.println("[EPG] Xtream short EPG FAILED: ${shortEpgAttempt.exceptionOrNull()?.message}")
                    }
                }

                // ── Slow path: XMLTV download (always runs to fill remaining channels) ──
                if (epgCandidates.isNotEmpty()) {
                    val epgCandidatesToTry = epgCandidates
                    var xmltvResolved = false
                    epgCandidatesToTry.forEachIndexed { index, epgUrl ->
                        if (xmltvResolved) return@forEachIndexed
                        val pct = (90 + ((index * 8) / epgCandidatesToTry.size.coerceAtLeast(1))).coerceIn(90, 98)
                        onProgress(IptvLoadProgress("Loading full EPG (${index + 1}/${epgCandidatesToTry.size})...", pct))
                        val attempt = runCatching {
                            withTimeoutOrNull(90_000L) { fetchAndParseEpg(epgUrl, channels) }
                                ?: throw java.util.concurrent.TimeoutException("EPG download timed out for ${epgUrl.take(80)}")
                        }
                        if (attempt.isSuccess) {
                            val parsed = attempt.getOrDefault(emptyMap())
                            val parsedHasPrograms = hasAnyProgramData(parsed)
                            if (parsedHasPrograms || index == epgCandidatesToTry.lastIndex) {
                                // Merge: XMLTV as base, then overlay short EPG (fresher per-channel data)
                                val merged = ConcurrentHashMap(parsed)
                                shortEpgResult?.let { merged.putAll(it) } // Short EPG wins for channels it covers
                                resolvedNowNext = merged
                                cachedNowNext = merged
                                cachedEpgAt = System.currentTimeMillis()
                                epgUpdated = true
                                preferredDerivedEpgUrl = epgUrl
                                resolved = true
                                xmltvResolved = true
                                System.err.println("[EPG] XMLTV SUCCESS: ${parsed.size} from XMLTV + ${shortEpgResult?.size ?: 0} from short EPG = ${merged.size} total")
                            }
                        } else {
                            epgFailureMessage = attempt.exceptionOrNull()?.message
                            System.err.println("[EPG] XMLTV attempt ${index + 1} failed: ${epgFailureMessage}")
                        }
                    }
                }

                if (!resolved) {
                    // Throttle repeated failures to avoid refetching every open.
                    cachedNowNext = ConcurrentHashMap()
                    cachedEpgAt = System.currentTimeMillis()
                    epgUpdated = true
                }
                resolvedNowNext
            }
            val epgFailure = epgFailureMessage
            val epgWarning = if (epgCandidates.isNotEmpty() && nowNext.isEmpty()) {
                if (!epgFailure.isNullOrBlank()) {
                    "EPG unavailable right now (${epgFailure.take(120)})."
                } else {
                    "EPG unavailable for this source right now."
                }
            } else null

            val favoriteGroups = observeFavoriteGroups().first()
            val favoriteChannels = observeFavoriteChannels().first()
            val grouped = channels.groupBy { it.group.ifBlank { "Uncategorized" } }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)

            val loadedAtMillis = if (cachedPlaylistAt > 0L) cachedPlaylistAt else now
            val loadedAtInstant = Instant.ofEpochMilli(loadedAtMillis)

            IptvSnapshot(
                channels = channels,
                grouped = grouped,
                nowNext = nowNext,
                favoriteGroups = favoriteGroups,
                favoriteChannels = favoriteChannels,
                epgWarning = epgWarning,
                loadedAt = loadedAtInstant
            ).also {
                if (forcePlaylistReload || forceEpgReload || cachedFromDisk == null || epgUpdated) {
                    writeCache(
                        config = config,
                        channels = channels,
                        nowNext = nowNext,
                        loadedAtMs = System.currentTimeMillis()
                    )
                    // Only update last refresh time when we actually refresh data from network
                    setLastRefreshTime(System.currentTimeMillis())
                }
                
                // Fetch and cache VOD and Series categories for Movies/Series screens
                runCatching {
                    val vodCats = getVodCategories()
                    cachedVodCategories = vodCats
                }.onFailure {
                    System.err.println("[IPTV] Failed to cache VOD categories: ${it.message}")
                }
                runCatching {
                    val seriesCats = getSeriesCategories()
                    cachedSeriesCategories = seriesCats
                }.onFailure {
                    System.err.println("[IPTV] Failed to cache Series categories: ${it.message}")
                }
                
                onProgress(IptvLoadProgress("Loaded ${channels.size} channels", 100))
            }
            }
        }
    }

    /**
     * Cache-only warmup used at app start.
     * Never performs network calls, so startup cannot get blocked by heavy playlists.
     */
    suspend fun warmupFromCacheOnly() {
        withContext(Dispatchers.IO) {
            loadMutex.withLock {
                val config = observeConfig().first()
                val profileId = profileManager.getProfileIdSync()
                ensureCacheOwnership(profileId, config)
                if (config.m3uUrl.isBlank()) return@withLock
                if (cachedChannels.isNotEmpty()) return@withLock

                val cached = readCache(config) ?: return@withLock
                cachedChannels = cached.channels
                cachedNowNext = ConcurrentHashMap(cached.nowNext)
                cachedPlaylistAt = cached.loadedAtEpochMs
                cachedEpgAt = cached.loadedAtEpochMs
            }
        }
    }

    /**
     * Returns the latest snapshot from memory/disk cache only.
     * Never performs network calls.
     */
    suspend fun getCachedSnapshotOrNull(): IptvSnapshot? {
        return withContext(Dispatchers.IO) {
            loadMutex.withLock {
                val config = observeConfig().first()
                val profileId = profileManager.getProfileIdSync()
                ensureCacheOwnership(profileId, config)

                if (config.m3uUrl.isBlank()) {
                    return@withLock IptvSnapshot(
                        channels = emptyList(),
                        grouped = emptyMap(),
                        nowNext = emptyMap(),
                        favoriteGroups = observeFavoriteGroups().first(),
                        favoriteChannels = observeFavoriteChannels().first(),
                        loadedAt = Instant.now()
                    )
                }

                if (cachedChannels.isEmpty()) {
                    val cached = readCache(config) ?: return@withLock null
                    cachedChannels = cached.channels
                    cachedNowNext = ConcurrentHashMap(cached.nowNext)
                    cachedPlaylistAt = cached.loadedAtEpochMs
                    cachedEpgAt = cached.loadedAtEpochMs
                }

                val favoriteGroups = observeFavoriteGroups().first()
                val favoriteChannels = observeFavoriteChannels().first()
                val grouped = cachedChannels.groupBy { it.group.ifBlank { "Uncategorized" } }
                    .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                val loadedAtMillis = if (cachedPlaylistAt > 0L) cachedPlaylistAt else System.currentTimeMillis()

                IptvSnapshot(
                    channels = cachedChannels,
                    grouped = grouped,
                    nowNext = cachedNowNext,
                    favoriteGroups = favoriteGroups,
                    favoriteChannels = favoriteChannels,
                    epgWarning = null,
                    loadedAt = Instant.ofEpochMilli(loadedAtMillis)
                )
            }
        }
    }

    fun isSnapshotStale(snapshot: IptvSnapshot): Boolean {
        val ageMs = System.currentTimeMillis() - snapshot.loadedAt.toEpochMilli()
        return ageMs > staleAfterMs
    }

    /** Age of cached EPG data in milliseconds, or Long.MAX_VALUE if no cache. */
    fun cachedEpgAgeMs(): Long {
        val at = cachedEpgAt
        return if (at <= 0L) Long.MAX_VALUE else System.currentTimeMillis() - at
    }

    /**
     * Non-blocking in-memory snapshot read. Returns null if in-memory cache is empty.
     * Unlike [getCachedSnapshotOrNull], this does NOT acquire [loadMutex] and does NOT
     * fall back to disk — it only reads volatile in-memory fields.
     * Use this when you need a fast, contention-free read (e.g., on navigation).
     */
    suspend fun getMemoryCachedSnapshot(): IptvSnapshot? {
        val channels = cachedChannels
        if (channels.isEmpty()) return null
        val favoriteGroups = observeFavoriteGroups().first()
        val favoriteChannels = observeFavoriteChannels().first()
        val grouped = channels.groupBy { it.group.ifBlank { "Uncategorized" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        val loadedAtMillis = if (cachedPlaylistAt > 0L) cachedPlaylistAt else System.currentTimeMillis()
        return IptvSnapshot(
            channels = channels,
            grouped = grouped,
            nowNext = cachedNowNext,
            favoriteGroups = favoriteGroups,
            favoriteChannels = favoriteChannels,
            epgWarning = null,
            loadedAt = Instant.ofEpochMilli(loadedAtMillis)
        )
    }

    /**
     * Re-derive now/next from cached EPG program data without any network call.
     * Programs shift: if "now" has ended, "next" becomes "now", etc.
     * Updates cachedNowNext in place so subsequent reads via getCachedSnapshotOrNull()
     * return the re-derived data.
     * Returns updated nowNext map for the given channel IDs, or null if no cached data.
     */
    fun reDeriveCachedNowNext(channelIds: Set<String>): Map<String, IptvNowNext>? {
        val cached = cachedNowNext
        if (cached.isEmpty()) return null
        val nowMs = System.currentTimeMillis()
        val recentCutoff = nowMs - (15L * 60_000L)

        val result = mutableMapOf<String, IptvNowNext>()
        for (channelId in channelIds) {
            val existing = cached[channelId] ?: continue
            // Collect all known programs from the cached entry
            val allPrograms = buildList {
                existing.now?.let { add(it) }
                existing.next?.let { add(it) }
                existing.later?.let { add(it) }
                addAll(existing.upcoming)
                addAll(existing.recent)
            }.sortedBy { it.startUtcMillis }

            var now: IptvProgram? = null
            var next: IptvProgram? = null
            var later: IptvProgram? = null
            val upcoming = mutableListOf<IptvProgram>()
            val recent = mutableListOf<IptvProgram>()

            for (p in allPrograms) {
                when {
                    p.endUtcMillis <= nowMs && p.endUtcMillis > recentCutoff -> recent.add(p)
                    p.isLive(nowMs) -> now = p
                    p.startUtcMillis > nowMs && next == null -> next = p
                    p.startUtcMillis > nowMs && later == null -> later = p
                    p.startUtcMillis > nowMs -> upcoming.add(p)
                }
            }

            result[channelId] = IptvNowNext(
                now = now,
                next = next,
                later = later,
                upcoming = upcoming.take(5),
                recent = recent
            )
        }
        if (result.isEmpty()) return null

        // Write back re-derived entries into cachedNowNext (in-place, no copy)
        cachedNowNext.putAll(result)

        return result
    }

    /**
     * Lightweight EPG refresh for specific channels using Xtream short EPG API.
     * Only fetches EPG for the given channel IDs. Updates cachedNowNext in place.
     * Returns the updated nowNext entries for those channels, or null if not an Xtream provider.
     */
    suspend fun refreshEpgForChannels(channelIds: Set<String>): Map<String, IptvNowNext>? {
        if (channelIds.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            val channels = cachedChannels.filter { it.id in channelIds }
            if (channels.isEmpty()) return@withContext null

            // Build lookups for these channels only
            val epgIdToChannelIds = mutableMapOf<String, MutableList<String>>()
            val streamIdToChannelIds = mutableMapOf<String, MutableList<String>>()
            for (ch in channels) {
                ch.epgId?.let { eid ->
                    epgIdToChannelIds.getOrPut(eid) { mutableListOf() }.add(ch.id)
                }
                resolveXtreamStreamId(ch)?.let { sid ->
                    streamIdToChannelIds.getOrPut(sid.toString()) { mutableListOf() }.add(ch.id)
                }
            }

            val xtreamChannels = channels.filter { resolveXtreamStreamId(it) != null }
            if (xtreamChannels.isEmpty()) return@withContext null

            System.err.println("[EPG-Refresh] Fetching short EPG for ${xtreamChannels.size} favorite channels")

            val allListings = java.util.Collections.synchronizedList(mutableListOf<XtreamEpgListing>())
            val errorCount = java.util.concurrent.atomic.AtomicInteger(0)
            // Use a small thread pool — this is just favorites (typically <20 channels)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(10.coerceAtMost(xtreamChannels.size))

            for (ch in xtreamChannels) {
                val sid = resolveXtreamStreamId(ch) ?: continue
                executor.submit {
                    val url = "${creds.baseUrl}/player_api.php?username=${creds.username}" +
                        "&password=${creds.password}&action=get_short_epg&stream_id=$sid&limit=5"
                    try {
                        val resp: XtreamEpgResponse? = requestJson(url, XtreamEpgResponse::class.java)
                        resp?.epgListings?.let { allListings.addAll(it) }
                    } catch (_: Exception) { errorCount.incrementAndGet() }
                }
            }

            try {
                executor.shutdown()
                executor.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: Exception) {
                executor.shutdownNow()
            }

            val errors = errorCount.get()
            System.err.println("[EPG-Refresh] Done: ${allListings.size} listings, $errors errors")

            if (allListings.isEmpty()) return@withContext null

            val freshNowNext = buildNowNextFromXtreamListings(allListings, epgIdToChannelIds, streamIdToChannelIds)
            if (freshNowNext.isEmpty()) return@withContext null

            // Merge into cache (in-place, no copy)
            cachedNowNext.putAll(freshNowNext)
            cachedEpgAt = System.currentTimeMillis()

            System.err.println("[EPG-Refresh] Updated ${freshNowNext.size} channels in cache")
            freshNowNext
        }
    }

    fun invalidateCache() {
        cachedChannels = emptyList()
        cachedNowNext = ConcurrentHashMap()
        cachedPlaylistAt = 0L
        cachedEpgAt = 0L
        discoveredM3uEpgUrl = null
        xtreamVodCacheKey = null
        xtreamVodLoadedAtMs = 0L
        xtreamSeriesLoadedAtMs = 0L
        cachedXtreamVodStreams = emptyList()
        cachedVodIndex = null
        cachedXtreamSeries = emptyList()
        cachedXtreamSeriesEpisodes = emptyMap()
        xtreamSeriesEpisodeInFlight = emptyMap()
        cachedVodCategories = emptyList()
        cachedSeriesCategories = emptyList()
        cacheOwnerProfileId = null
        cacheOwnerConfigSig = null
        // Clear disk-cached VOD/series catalogs
        runCatching { xtreamDiskCacheDir().deleteRecursively() }
    }

    private fun ensureCacheOwnership(profileId: String, config: IptvConfig) {
        val sig = "${config.m3uUrl.trim()}|${config.epgUrl.trim()}"
        val ownerChanged = cacheOwnerProfileId != null && cacheOwnerProfileId != profileId
        val configChanged = cacheOwnerConfigSig != null && cacheOwnerConfigSig != sig
        if (ownerChanged || configChanged) {
            invalidateCache()
        }
        cacheOwnerProfileId = profileId
        cacheOwnerConfigSig = sig
    }

    private fun m3uUrlKey(): Preferences.Key<String> = profileManager.profileStringKey("iptv_m3u_url")
    private fun m3uUrlKeyFor(profileId: String): Preferences.Key<String> =
        profileManager.profileStringKeyFor(profileId, "iptv_m3u_url")
    private fun epgUrlKey(): Preferences.Key<String> = profileManager.profileStringKey("iptv_epg_url")
    private fun epgUrlKeyFor(profileId: String): Preferences.Key<String> =
        profileManager.profileStringKeyFor(profileId, "iptv_epg_url")
    private fun xtreamUsernameKey(): Preferences.Key<String> = profileManager.profileStringKey("iptv_xtream_username")
    private fun xtreamUsernameKeyFor(profileId: String): Preferences.Key<String> =
        profileManager.profileStringKeyFor(profileId, "iptv_xtream_username")
    private fun xtreamPasswordKey(): Preferences.Key<String> = profileManager.profileStringKey("iptv_xtream_password")
    private fun xtreamPasswordKeyFor(profileId: String): Preferences.Key<String> =
        profileManager.profileStringKeyFor(profileId, "iptv_xtream_password")
    private fun favoriteGroupsKey(): Preferences.Key<String> = profileManager.profileStringKey("iptv_favorite_groups")
    private fun favoriteGroupsKeyFor(profileId: String): Preferences.Key<String> =
        profileManager.profileStringKeyFor(profileId, "iptv_favorite_groups")
    private fun favoriteChannelsKey(): Preferences.Key<String> = profileManager.profileStringKey("iptv_favorite_channels")
    private fun favoriteChannelsKeyFor(profileId: String): Preferences.Key<String> =
        profileManager.profileStringKeyFor(profileId, "iptv_favorite_channels")
    private fun refreshIntervalKey(): Preferences.Key<Long> = profileManager.profileLongKey("iptv_refresh_interval_hours")
    private fun refreshIntervalKeyFor(profileId: String): Preferences.Key<Long> =
        profileManager.profileLongKeyFor(profileId, "iptv_refresh_interval_hours")
    private fun lastRefreshTimeKey(): Preferences.Key<Long> = profileManager.profileLongKey("iptv_last_refresh_time_ms")
    private fun lastRefreshTimeKeyFor(profileId: String): Preferences.Key<Long> =
        profileManager.profileLongKeyFor(profileId, "iptv_last_refresh_time_ms")

    private fun decodeFavoriteGroups(prefs: Preferences): List<String> {
        val raw = prefs[favoriteGroupsKey()].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun decodeFavoriteChannels(prefs: Preferences): List<String> {
        val raw = prefs[favoriteChannelsKey()].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun decodeFavoriteGroups(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun decodeFavoriteChannels(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun exportCloudConfigForProfile(profileId: String): IptvCloudProfileState {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val prefs = context.settingsDataStore.data.first()
        return IptvCloudProfileState(
            m3uUrl = decryptConfigValue(prefs[m3uUrlKeyFor(safeProfileId)].orEmpty()),
            epgUrl = decryptConfigValue(prefs[epgUrlKeyFor(safeProfileId)].orEmpty()),
            favoriteGroups = decodeFavoriteGroups(prefs[favoriteGroupsKeyFor(safeProfileId)].orEmpty()),
            favoriteChannels = decodeFavoriteChannels(prefs[favoriteChannelsKeyFor(safeProfileId)].orEmpty())
        )
    }

    suspend fun importCloudConfigForProfile(profileId: String, state: IptvCloudProfileState) {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val normalizedM3u = normalizeIptvInput(state.m3uUrl)
        val normalizedEpg = normalizeEpgInput(state.epgUrl)
        context.settingsDataStore.edit { prefs ->
            prefs[m3uUrlKeyFor(safeProfileId)] = encryptConfigValue(normalizedM3u)
            prefs[epgUrlKeyFor(safeProfileId)] = encryptConfigValue(normalizedEpg)
            prefs[favoriteGroupsKeyFor(safeProfileId)] = gson.toJson(state.favoriteGroups.distinct())
            prefs[favoriteChannelsKeyFor(safeProfileId)] = gson.toJson(state.favoriteChannels.distinct())
        }
        if (profileManager.getProfileIdSync() == safeProfileId) {
            invalidateCache()
        }
    }

    private suspend fun fetchAndParseM3uWithRetries(
        url: String,
        onProgress: (IptvLoadProgress) -> Unit
    ): List<IptvChannel> {
        resolveXtreamCredentials(url)?.let { creds ->
            onProgress(IptvLoadProgress("Detected Xtream provider. Loading live channels...", 6))
            runCatching { fetchXtreamLiveChannels(creds, onProgress) }
                .onSuccess { channels ->
                    if (channels.isNotEmpty()) {
                        onProgress(IptvLoadProgress("Loaded ${channels.size} live channels from provider API", 95))
                        return channels
                    }
                }
        }

        var lastError: Throwable? = null
        val maxAttempts = 2
        repeat(maxAttempts) { attempt ->
            onProgress(IptvLoadProgress("Connecting to playlist (attempt ${attempt + 1}/$maxAttempts)...", 5))
            runCatching {
                fetchAndParseM3uOnce(url, onProgress)
            }.onSuccess { channels ->
                if (channels.isNotEmpty()) return channels
                lastError = IllegalStateException("Playlist loaded but contains no channels.")
            }.onFailure { error ->
                lastError = error
            }

            if (attempt < maxAttempts - 1) {
                val backoffMs = (1_000L * (attempt + 1)).coerceAtMost(2_000L)
                onProgress(IptvLoadProgress("Retrying in ${backoffMs / 1000}s...", 5))
                delay(backoffMs)
            }
        }
        throw (lastError ?: IllegalStateException("Failed to load M3U playlist."))
    }

    private data class XtreamCredentials(
        val baseUrl: String,
        val username: String,
        val password: String
    )

    private data class XtreamLiveCategory(
        @SerializedName("category_id") val categoryId: String? = null,
        @SerializedName("category_name") val categoryName: String? = null
    )

    private data class XtreamLiveStream(
        @SerializedName("stream_id") val streamId: Int? = null,
        val name: String? = null,
        @SerializedName("stream_icon") val streamIcon: String? = null,
        @SerializedName("epg_channel_id") val epgChannelId: String? = null,
        @SerializedName("category_id") val categoryId: String? = null
    )

    data class XtreamVodStream(
        @SerializedName("stream_id") val streamId: Int? = null,
        val name: String? = null,
        val year: String? = null,
        @SerializedName("container_extension") val containerExtension: String? = null,
        @SerializedName(value = "imdb", alternate = ["imdb_id", "imdbid"]) val imdb: String? = null,
        @SerializedName(value = "tmdb", alternate = ["tmdb_id", "tmdbid"]) val tmdb: String? = null,
        @SerializedName("category_id") val categoryId: String? = null,
        @SerializedName("stream_icon") val streamIcon: String? = null
    )

    data class XtreamVodCategory(
        @SerializedName("category_id") val categoryId: String,
        @SerializedName("category_name") val categoryName: String
    )

    data class XtreamSeriesCategory(
        @SerializedName("category_id") val categoryId: String,
        @SerializedName("category_name") val categoryName: String
    )

    data class XtreamSeriesItem(
        @SerializedName(value = "series_id", alternate = ["seriesid", "id"]) val seriesId: Int? = null,
        val name: String? = null,
        @SerializedName(value = "imdb", alternate = ["imdb_id", "imdbid"]) val imdb: String? = null,
        @SerializedName(value = "tmdb", alternate = ["tmdb_id", "tmdbid"]) val tmdb: String? = null,
        @SerializedName("category_id") val categoryId: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("backdrop") val backdrop: String? = null
    )

    /**
     * Indexed catalog for VOD streams — enables O(1) lookups by TMDB, IMDB, and canonical title.
     * Memory-efficient: stores indices pointing to original list positions instead of copying items.
     * Total overhead: ~5-10MB for 60K items (maps + normalized strings).
     */
    private data class VodCatalogIndex(
        val createdAtMs: Long,
        val itemCount: Int,
        /** TMDB ID → list indices (normalized: "12345") */
        val tmdbMap: Map<String, List<Int>>,
        /** IMDB ID → list indices (normalized: "tt1234567") */
        val imdbMap: Map<String, List<Int>>,
        /** Canonical title → list indices (sorted tokens: "bad breaking") */
        val canonicalTitleMap: Map<String, List<Int>>,
        /** Token → list indices (individual words: "breaking", "bad") */
        val tokenMap: Map<String, List<Int>>
    )

    data class XtreamVodInfo(
        val info: VodInfoDetails? = null,
        @SerializedName("movie_data") val movieData: VodMovieData? = null
    )

    data class VodInfoDetails(
        @SerializedName("tmdb_id") val tmdbId: String? = null,
        val name: String? = null,
        val description: String? = null,
        @SerializedName("cover_big") val coverBig: String? = null,
        val genre: String? = null,
        val director: String? = null,
        val cast: String? = null,
        val releasedate: String? = null,
        @SerializedName("episode_run_time") val episodeRunTime: String? = null
    )

    data class VodMovieData(
        @SerializedName("stream_id") val streamId: Int? = null,
        val name: String? = null,
        @SerializedName("category_id") val categoryId: String? = null,
        @SerializedName("container_extension") val extension: String = "mp4"
    )

    private data class XtreamSeriesEpisode(
        val id: Int,
        val season: Int,
        val episode: Int,
        val title: String,
        val containerExtension: String?
    )

    private data class ResolverSeriesEntry(
        val seriesId: Int,
        val name: String = "",
        val normalizedName: String = "",
        val canonicalTitleKey: String = "",
        val titleTokens: Set<String> = emptySet(),
        val tmdb: String?,
        val imdb: String?,
        val year: Int?
    )

    private data class ResolverCatalogIndex(
        val createdAtMs: Long,
        val entries: List<ResolverSeriesEntry>,
        val tmdbMap: Map<String, List<ResolverSeriesEntry>>,
        val imdbMap: Map<String, List<ResolverSeriesEntry>>,
        val canonicalTitleMap: Map<String, List<ResolverSeriesEntry>>,
        val tokenMap: Map<String, List<ResolverSeriesEntry>>
    )

    private data class ResolverCandidate(
        val entry: ResolverSeriesEntry,
        val confidence: Float,
        val method: String,
        val baseScore: Int
    )

    private data class ResolverEpisodeHit(
        val episode: XtreamSeriesEpisode,
        val score: Int
    )

    private data class ResolverCachedResolvedEpisode(
        val streamId: Int,
        val containerExtension: String?,
        val seriesId: Int,
        val confidence: Float,
        val method: String,
        val savedAtMs: Long
    )

    private data class ResolverPersistedCatalog(
        val createdAtMs: Long = 0L,
        val entries: List<ResolverSeriesEntry> = emptyList()
    )

    private data class ResolverPersistedResolved(
        val items: Map<String, ResolverCachedResolvedEpisode> = emptyMap()
    )

    private data class ResolverPersistedSeriesInfo(
        val savedAtMs: Long = 0L,
        val episodes: List<XtreamSeriesEpisode> = emptyList()
    )

    private data class ResolverPersistedSeriesBindings(
        val items: Map<String, Int> = emptyMap()
    )

    private inner class IptvSeriesResolverService {
        private val prefs by lazy { context.getSharedPreferences("iptv_series_resolver_cache_v1", Context.MODE_PRIVATE) }
        private val catalogLoadMutex = Mutex()
        private val catalogTtlMs = 24 * 60 * 60_000L
        private val resolvedTtlMs = 24 * 60 * 60_000L
        private val seriesInfoTtlMs = 24 * 60 * 60_000L
        private val catalogMemory = ConcurrentHashMap<String, ResolverCatalogIndex>()
        private val resolvedMemory = object : LinkedHashMap<String, ResolverCachedResolvedEpisode>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolverCachedResolvedEpisode>?): Boolean {
                return size > 512
            }
        }
        private val resolvedLock = Any()
        private val seriesBindingMemory = object : LinkedHashMap<String, Int>(2048, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean {
                return size > 2048
            }
        }
        private val seriesBindingLock = Any()
        private val seriesInfoMemory = object : LinkedHashMap<String, List<XtreamSeriesEpisode>>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<XtreamSeriesEpisode>>?): Boolean {
                return size > 50
            }
        }
        private val seriesInfoLock = Any()

        private fun catalogPrefKey(providerKey: String): String = "catalog_${providerKey.hashCode()}"
        private val resolvedPrefKey = "resolved_episode_map"
        private val seriesBindingPrefKey = "series_binding_map"
        private fun seriesInfoPrefKey(providerKey: String, seriesId: Int): String =
            "series_info_${(providerKey + "|" + seriesId).hashCode()}"

        suspend fun refreshCatalog(
            providerKey: String,
            creds: XtreamCredentials
        ) {
            loadCatalog(providerKey, creds, allowNetwork = true, forceRefresh = true)
        }

        suspend fun prefetchSeriesInfo(
            providerKey: String,
            creds: XtreamCredentials,
            showTitle: String,
            tmdbId: Int?,
            imdbId: String?,
            year: Int?
        ) {
            val normalizedShow = normalizeLookupText(showTitle)
            val normalizedTmdb = normalizeTmdbId(tmdbId)
            val normalizedImdb = normalizeImdbId(imdbId)
            if (normalizedShow.isBlank() && normalizedTmdb.isNullOrBlank() && normalizedImdb.isNullOrBlank()) return

            val catalog = loadCatalog(providerKey, creds, allowNetwork = true, forceRefresh = false)
            if (catalog.entries.isEmpty()) return
            val candidates = buildCandidates(catalog, normalizedShow, normalizedTmdb, normalizedImdb, year)
            if (candidates.isEmpty()) return

            val probeList = if (candidates.first().confidence >= 0.9f) {
                candidates.take(1)
            } else {
                candidates.take(2)
            }
            coroutineScope {
                probeList.map { candidate ->
                    async {
                        withTimeoutOrNull(5_000L) {
                            loadSeriesInfo(
                                providerKey = providerKey,
                                creds = creds,
                                seriesId = candidate.entry.seriesId,
                                allowNetwork = true
                            )
                        }
                    }
                }.awaitAll()
            }
        }

        suspend fun resolveEpisode(
            providerKey: String,
            creds: XtreamCredentials,
            showTitle: String,
            season: Int,
            episode: Int,
            tmdbId: Int?,
            imdbId: String?,
            year: Int?,
            allowNetwork: Boolean
        ): ResolverCachedResolvedEpisode? {
            val resolveStart = System.currentTimeMillis()
            System.err.println("[VOD-Resolver] resolveEpisode start: '$showTitle' S${season}E${episode} tmdb=$tmdbId imdb=$imdbId")
            val normalizedShow = normalizeLookupText(showTitle)
            val normalizedTmdb = normalizeTmdbId(tmdbId)
            val normalizedImdb = normalizeImdbId(imdbId)
            if (normalizedShow.isBlank() && normalizedTmdb.isNullOrBlank() && normalizedImdb.isNullOrBlank()) {
                System.err.println("[VOD-Resolver] No identifiers, returning null")
                return null
            }

            val cacheKey = buildResolvedCacheKey(providerKey, normalizedTmdb, normalizedImdb, normalizedShow, season, episode)
            readResolved(cacheKey)?.let { cached ->
                if (System.currentTimeMillis() - cached.savedAtMs < resolvedTtlMs) {
                    System.err.println("[VOD-Resolver] Hit resolved cache (method=${cached.method}, streamId=${cached.streamId}) in ${System.currentTimeMillis() - resolveStart}ms")
                    return cached
                }
            }

            val bindingKeys = buildSeriesBindingKeys(
                providerKey = providerKey,
                normalizedTmdb = normalizedTmdb,
                normalizedImdb = normalizedImdb,
                normalizedShow = normalizedShow
            )
            readSeriesBinding(bindingKeys)?.let { boundSeriesId ->
                System.err.println("[VOD-Resolver] Found series binding: seriesId=$boundSeriesId, loading info...")
                val bindStart = System.currentTimeMillis()
                val boundEpisodes = loadSeriesInfo(
                    providerKey = providerKey,
                    creds = creds,
                    seriesId = boundSeriesId,
                    allowNetwork = allowNetwork
                )
                System.err.println("[VOD-Resolver] loadSeriesInfo for binding took ${System.currentTimeMillis() - bindStart}ms, got ${boundEpisodes.size} episodes")
                val boundHit = matchEpisode(boundEpisodes, season, episode)
                if (boundHit != null) {
                    val resolved = ResolverCachedResolvedEpisode(
                        streamId = boundHit.episode.id,
                        containerExtension = boundHit.episode.containerExtension,
                        seriesId = boundSeriesId,
                        confidence = 0.995f,
                        method = "series_binding",
                        savedAtMs = System.currentTimeMillis()
                    )
                    writeResolved(cacheKey, resolved)
                    writeSeriesBinding(bindingKeys, boundSeriesId)
                    System.err.println("[VOD-Resolver] Resolved via binding in ${System.currentTimeMillis() - resolveStart}ms")
                    return resolved
                }
                System.err.println("[VOD-Resolver] Binding didn't match S${season}E${episode}")
            }

            System.err.println("[VOD-Resolver] Loading catalog...")
            val catalogStart = System.currentTimeMillis()
            val catalog = loadCatalog(providerKey, creds, allowNetwork = allowNetwork, forceRefresh = false)
            System.err.println("[VOD-Resolver] loadCatalog took ${System.currentTimeMillis() - catalogStart}ms, entries=${catalog.entries.size}")
            if (catalog.entries.isEmpty()) {
                System.err.println("[VOD-Resolver] Empty catalog, returning null")
                return null
            }

            val candidateStart = System.currentTimeMillis()
            val candidates = buildCandidates(catalog, normalizedShow, normalizedTmdb, normalizedImdb, year)
            System.err.println("[VOD-Resolver] buildCandidates took ${System.currentTimeMillis() - candidateStart}ms, found ${candidates.size} candidates")
            if (candidates.isEmpty()) {
                System.err.println("[VOD-Resolver] No candidates, returning null after ${System.currentTimeMillis() - resolveStart}ms")
                return null
            }
            val probeList = if (
                candidates.first().method == "tmdb_id" ||
                candidates.first().method == "imdb_id" ||
                candidates.first().method == "title_canonical"
            ) {
                candidates.take(1)
            } else {
                candidates.take(2)
            }
            System.err.println("[VOD-Resolver] Probing ${probeList.size} candidates: ${probeList.map { "${it.entry.name}(${it.method},${it.confidence})" }}")

            val probeStart = System.currentTimeMillis()
            val hits = coroutineScope {
                probeList.map { candidate ->
                    async {
                        val infoStart = System.currentTimeMillis()
                        val episodes = loadSeriesInfo(providerKey, creds, candidate.entry.seriesId, allowNetwork)
                        System.err.println("[VOD-Resolver] loadSeriesInfo(${candidate.entry.seriesId}) took ${System.currentTimeMillis() - infoStart}ms, got ${episodes.size} episodes")
                        val hit = matchEpisode(episodes, season, episode) ?: return@async null
                        Triple(candidate, hit.episode, hit.score)
                    }
                }.awaitAll().filterNotNull()
            }
            System.err.println("[VOD-Resolver] Probing took ${System.currentTimeMillis() - probeStart}ms, hits=${hits.size}")
            if (hits.isEmpty()) {
                System.err.println("[VOD-Resolver] No hits, returning null after ${System.currentTimeMillis() - resolveStart}ms")
                return null
            }

            val best = hits.maxByOrNull { it.first.confidence * 1000f + it.third } ?: return null
            val resolved = ResolverCachedResolvedEpisode(
                streamId = best.second.id,
                containerExtension = best.second.containerExtension,
                seriesId = best.first.entry.seriesId,
                confidence = best.first.confidence,
                method = best.first.method,
                savedAtMs = System.currentTimeMillis()
            )
            writeResolved(cacheKey, resolved)
            writeSeriesBinding(bindingKeys, best.first.entry.seriesId)
            System.err.println("[VOD-Resolver] Resolved via ${best.first.method} (conf=${best.first.confidence}) in ${System.currentTimeMillis() - resolveStart}ms")
            return resolved
        }

        private suspend fun loadCatalog(
            providerKey: String,
            creds: XtreamCredentials,
            allowNetwork: Boolean,
            forceRefresh: Boolean
        ): ResolverCatalogIndex {
            // Fast path: check in-memory cache (no lock needed)
            val now = System.currentTimeMillis()
            val inMem = catalogMemory[providerKey]
            if (!forceRefresh && inMem != null && now - inMem.createdAtMs < catalogTtlMs) return inMem

            if (!allowNetwork) {
                if (inMem != null) return inMem
                // Try SharedPreferences for stale data
                val persistedRaw = runCatching { prefs.getString(catalogPrefKey(providerKey), null) }.getOrNull()
                if (!persistedRaw.isNullOrBlank()) {
                    val persisted = runCatching { gson.fromJson(persistedRaw, ResolverPersistedCatalog::class.java) }.getOrNull()
                    if (persisted != null && persisted.entries.isNotEmpty()) {
                        val built = buildCatalogIndex(persisted.createdAtMs, persisted.entries)
                        catalogMemory[providerKey] = built
                        return built
                    }
                }
                return ResolverCatalogIndex(now, emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
            }

            // Serialize catalog builds — only one thread does the expensive work,
            // others wait and get the result from memory.
            // Use NonCancellable so a cancelled coroutine doesn't abandon the build
            // while holding the mutex, leaving catalogMemory empty for everyone.
            return withContext(kotlinx.coroutines.NonCancellable) { catalogLoadMutex.withLock {
                // Re-check memory inside lock — another thread may have built it while we waited
                val afterLockMem = catalogMemory[providerKey]
                val lockNow = System.currentTimeMillis()
                if (!forceRefresh && afterLockMem != null && lockNow - afterLockMem.createdAtMs < catalogTtlMs) {
                    System.err.println("[VOD-Resolver] loadCatalog: found in memory after lock wait (${afterLockMem.entries.size} entries)")
                    return@withLock afterLockMem
                }

                // Try SharedPreferences persisted catalog
                var stalePersisted: ResolverPersistedCatalog? = null
                if (!forceRefresh) {
                    val persistedRaw = runCatching { prefs.getString(catalogPrefKey(providerKey), null) }.getOrNull()
                    if (!persistedRaw.isNullOrBlank()) {
                        val persisted = runCatching { gson.fromJson(persistedRaw, ResolverPersistedCatalog::class.java) }.getOrNull()
                        if (persisted != null && persisted.entries.isNotEmpty()) {
                            stalePersisted = persisted
                            if (lockNow - persisted.createdAtMs < catalogTtlMs) {
                                System.err.println("[VOD-Resolver] loadCatalog: building from persisted prefs (${persisted.entries.size} entries)")
                                val built = buildCatalogIndex(persisted.createdAtMs, persisted.entries)
                                catalogMemory[providerKey] = built
                                return@withLock built
                            }
                        }
                    }
                }

                System.err.println("[VOD-Resolver] loadCatalog: fetching series list from network...")
                val fetchStart = System.currentTimeMillis()
                val entries = withTimeoutOrNull(45_000L) {
                    val rawList = getXtreamSeriesList(creds, allowNetwork = true, fast = false)
                    System.err.println("[VOD-Resolver] loadCatalog: got ${rawList.size} raw series in ${System.currentTimeMillis() - fetchStart}ms, building entries...")
                    rawList.mapNotNull { item ->
                        val seriesId = item.seriesId ?: return@mapNotNull null
                        val name = item.name?.trim().orEmpty()
                        if (name.isBlank()) return@mapNotNull null
                        val normalizedName = normalizeLookupText(name)
                        val tokens = extractTitleTokensFromNormalized(normalizedName)
                        ResolverSeriesEntry(
                            seriesId = seriesId,
                            name = name,
                            normalizedName = normalizedName,
                            canonicalTitleKey = toCanonicalTitleKeyFromTokens(tokens),
                            titleTokens = tokens,
                            tmdb = normalizeTmdbId(item.tmdb),
                            imdb = normalizeImdbId(item.imdb),
                            year = parseYear(item.name ?: "")
                        )
                    }
                }.orEmpty()
                System.err.println("[VOD-Resolver] loadCatalog: entries=${entries.size} in ${System.currentTimeMillis() - fetchStart}ms")

                if (entries.isEmpty()) {
                    val stale = stalePersisted
                    if (stale != null && stale.entries.isNotEmpty()) {
                        System.err.println("[VOD-Resolver] loadCatalog: network empty, using stale persisted (${stale.entries.size})")
                        val built = buildCatalogIndex(stale.createdAtMs, stale.entries)
                        catalogMemory[providerKey] = built
                        return@withLock built
                    }
                }

                val buildStart = System.currentTimeMillis()
                val built = buildCatalogIndex(lockNow, entries)
                System.err.println("[VOD-Resolver] loadCatalog: buildCatalogIndex took ${System.currentTimeMillis() - buildStart}ms")
                catalogMemory[providerKey] = built
                // Persist to SharedPreferences in background — don't block resolution
                if (entries.isNotEmpty() && entries.size <= 50_000) {
                    runCatching {
                        prefs.edit().putString(catalogPrefKey(providerKey), gson.toJson(ResolverPersistedCatalog(lockNow, entries))).apply()
                    }
                }
                built
            } }
        }

        private fun buildCatalogIndex(createdAtMs: Long, entries: List<ResolverSeriesEntry>): ResolverCatalogIndex {
            val normalizedEntries = entries.map { entry ->
                val normalizedName = entry.normalizedName.ifBlank { normalizeLookupText(entry.name) }
                val titleTokens = if (entry.titleTokens.isEmpty()) extractTitleTokensFromNormalized(normalizedName) else entry.titleTokens
                val canonicalTitleKey = entry.canonicalTitleKey.ifBlank { toCanonicalTitleKeyFromTokens(titleTokens) }
                entry.copy(
                    normalizedName = normalizedName,
                    canonicalTitleKey = canonicalTitleKey,
                    titleTokens = titleTokens
                )
            }
            // IMPORTANT: Normalize keys so lookup matches correctly
            val tmdbMap = normalizedEntries
                .filter { !it.tmdb.isNullOrBlank() }
                .groupBy { normalizeTmdbId(it.tmdb)!! }
            val imdbMap = normalizedEntries
                .filter { !it.imdb.isNullOrBlank() }
                .groupBy { normalizeImdbId(it.imdb)!! }
            val canonicalTitleMap = normalizedEntries
                .filter { it.canonicalTitleKey.isNotBlank() }
                .groupBy { it.canonicalTitleKey }
            val tokenMap = buildMap<String, List<ResolverSeriesEntry>> {
                val temp = LinkedHashMap<String, MutableList<ResolverSeriesEntry>>()
                normalizedEntries.forEach { entry ->
                    entry.titleTokens.forEach { token ->
                        temp.getOrPut(token) { mutableListOf() }.add(entry)
                    }
                }
                temp.forEach { (token, tokenEntries) ->
                    put(token, tokenEntries.distinctBy { it.seriesId })
                }
            }
            return ResolverCatalogIndex(
                createdAtMs = createdAtMs,
                entries = normalizedEntries,
                tmdbMap = tmdbMap,
                imdbMap = imdbMap,
                canonicalTitleMap = canonicalTitleMap,
                tokenMap = tokenMap
            )
        }

        private fun buildCandidates(
            catalog: ResolverCatalogIndex,
            normalizedShow: String,
            normalizedTmdb: String?,
            normalizedImdb: String?,
            inputYear: Int?
        ): List<ResolverCandidate> {
            val out = LinkedHashMap<Int, ResolverCandidate>()

            if (!normalizedTmdb.isNullOrBlank()) {
                catalog.tmdbMap[normalizedTmdb].orEmpty().forEach { entry ->
                    out[entry.seriesId] = ResolverCandidate(entry, confidence = 0.98f, method = "tmdb_id", baseScore = 20_000)
                }
            }
            if (!normalizedImdb.isNullOrBlank()) {
                catalog.imdbMap[normalizedImdb].orEmpty().forEach { entry ->
                    val prev = out[entry.seriesId]
                    if (prev == null || prev.confidence < 0.99f) {
                        out[entry.seriesId] = ResolverCandidate(entry, confidence = 0.99f, method = "imdb_id", baseScore = 21_000)
                    }
                }
            }

            if (normalizedShow.isNotBlank()) {
                val canonicalShow = toCanonicalTitleKey(normalizedShow)
                if (canonicalShow.isNotBlank()) {
                    catalog.canonicalTitleMap[canonicalShow].orEmpty().forEach { entry ->
                        val yearDelta = when {
                            inputYear == null || entry.year == null -> 0
                            else -> kotlin.math.abs(inputYear - entry.year)
                        }
                        if (yearDelta > 1) return@forEach
                        val total = when (yearDelta) {
                            0 -> 18_000
                            1 -> 17_500
                            else -> 17_200
                        }
                        val confidence = when (yearDelta) {
                            0 -> 0.93f
                            1 -> 0.90f
                            else -> 0.88f
                        }
                        val existing = out[entry.seriesId]
                        if (existing == null || total > existing.baseScore) {
                            out[entry.seriesId] = ResolverCandidate(entry, confidence = confidence, method = "title_canonical", baseScore = total)
                        }
                    }
                }

                val queryTokens = extractTitleTokens(normalizedShow)
                if (queryTokens.isNotEmpty()) {
                    val candidatePool = LinkedHashMap<Int, ResolverSeriesEntry>()
                    queryTokens.forEach { token ->
                        catalog.tokenMap[token].orEmpty().forEach { entry ->
                            candidatePool[entry.seriesId] = entry
                        }
                    }
                    candidatePool.values.forEach { entry ->
                        val overlap = entry.titleTokens.intersect(queryTokens).size
                        if (overlap <= 0) return@forEach
                        val coverage = overlap.toFloat() / queryTokens.size.toFloat()
                        val accepted = if (queryTokens.size == 1) {
                            coverage >= 1f
                        } else {
                            overlap >= 2 || coverage >= 0.6f
                        }
                        if (!accepted) return@forEach
                        val yearDelta = when {
                            inputYear == null || entry.year == null -> 0
                            else -> kotlin.math.abs(inputYear - entry.year)
                        }
                        if (yearDelta > 1) return@forEach
                        val yearScore = when (yearDelta) {
                            0 -> 120
                            1 -> 70
                            else -> 35
                        }
                        val total = (coverage * 1_000f).toInt() + (overlap * 180) + yearScore
                        val confidence = when {
                            coverage >= 1f && overlap >= 2 -> 0.86f
                            coverage >= 0.8f -> 0.82f
                            else -> 0.76f
                        }
                        val existing = out[entry.seriesId]
                        if (existing == null || total > existing.baseScore) {
                            out[entry.seriesId] = ResolverCandidate(entry, confidence = confidence, method = "title_tokens", baseScore = total)
                        }
                    }
                }
            }

            return out.values
                .sortedWith(compareByDescending<ResolverCandidate> { it.confidence }.thenByDescending { it.baseScore })
        }

        private suspend fun loadSeriesInfo(
            providerKey: String,
            creds: XtreamCredentials,
            seriesId: Int,
            allowNetwork: Boolean
        ): List<XtreamSeriesEpisode> {
            val key = "$providerKey|$seriesId"
            synchronized(seriesInfoLock) {
                val cached = seriesInfoMemory[key]
                if (!cached.isNullOrEmpty()) return cached
            }
            val persisted = runCatching {
                gson.fromJson(
                    prefs.getString(seriesInfoPrefKey(providerKey, seriesId), null),
                    ResolverPersistedSeriesInfo::class.java
                )
            }.getOrNull()
            if (persisted != null &&
                persisted.episodes.isNotEmpty() &&
                System.currentTimeMillis() - persisted.savedAtMs < seriesInfoTtlMs
            ) {
                synchronized(seriesInfoLock) {
                    seriesInfoMemory[key] = persisted.episodes
                }
                return persisted.episodes
            }
            val episodes = withTimeoutOrNull(10_000L) {
                getXtreamSeriesEpisodes(creds, seriesId, allowNetwork = allowNetwork, fast = false)
            }.orEmpty()
            if (episodes.isNotEmpty()) {
                synchronized(seriesInfoLock) {
                    seriesInfoMemory[key] = episodes
                }
                runCatching {
                    prefs.edit().putString(
                        seriesInfoPrefKey(providerKey, seriesId),
                        gson.toJson(
                            ResolverPersistedSeriesInfo(
                                savedAtMs = System.currentTimeMillis(),
                                episodes = episodes
                            )
                        )
                    ).apply()
                }
            }
            return episodes
        }

        private fun matchEpisode(
            episodes: List<XtreamSeriesEpisode>,
            requestedSeason: Int,
            requestedEpisode: Int
        ): ResolverEpisodeHit? {
            if (episodes.isEmpty()) return null

            // Exact season/episode is the only high-confidence match.
            episodes.firstOrNull { it.season == requestedSeason && it.episode == requestedEpisode }?.let {
                return ResolverEpisodeHit(it, score = 1000)
            }

            // If provider clearly has the requested season, do not cross-match to another season.
            if (episodes.any { it.season == requestedSeason }) {
                return null
            }

            // Flattened providers sometimes expose all episodes as season 1 (or 0).
            // Allow this only when there is a single unambiguous episode-number match.
            val sameEpisode = episodes.filter { it.episode == requestedEpisode }
            val flattened = episodes.all { it.season <= 1 }
            if (flattened && sameEpisode.size == 1) {
                return ResolverEpisodeHit(sameEpisode.first(), score = 640)
            }
            return null
        }

        private fun buildResolvedCacheKey(
            providerKey: String,
            tmdb: String?,
            imdb: String?,
            normalizedTitle: String,
            season: Int,
            episode: Int
        ): String {
            return listOf(
                providerKey,
                tmdb.orEmpty(),
                imdb.orEmpty(),
                normalizedTitle,
                season.toString(),
                episode.toString()
            ).joinToString("|")
        }

        private fun readResolved(key: String): ResolverCachedResolvedEpisode? {
            synchronized(resolvedLock) {
                resolvedMemory[key]?.let { return it }
            }
            val raw = prefs.getString(resolvedPrefKey, null) ?: return null
            val persisted = runCatching { gson.fromJson(raw, ResolverPersistedResolved::class.java) }.getOrNull() ?: return null
            val hit = persisted.items[key] ?: return null
            if (System.currentTimeMillis() - hit.savedAtMs > resolvedTtlMs) return null
            synchronized(resolvedLock) { resolvedMemory[key] = hit }
            return hit
        }

        private fun writeResolved(key: String, value: ResolverCachedResolvedEpisode) {
            synchronized(resolvedLock) {
                resolvedMemory[key] = value
            }
            val existingRaw = prefs.getString(resolvedPrefKey, null)
            val existing = runCatching { gson.fromJson(existingRaw, ResolverPersistedResolved::class.java) }.getOrNull()
                ?: ResolverPersistedResolved()
            val merged = LinkedHashMap(existing.items)
            merged[key] = value
            while (merged.size > 512) {
                val oldest = merged.entries.minByOrNull { it.value.savedAtMs }?.key ?: break
                merged.remove(oldest)
            }
            runCatching {
                prefs.edit().putString(resolvedPrefKey, gson.toJson(ResolverPersistedResolved(merged))).apply()
            }
        }

        private fun buildSeriesBindingKeys(
            providerKey: String,
            normalizedTmdb: String?,
            normalizedImdb: String?,
            normalizedShow: String
        ): List<String> {
            val keys = mutableListOf<String>()
            if (!normalizedTmdb.isNullOrBlank()) keys += "$providerKey|tmdb:$normalizedTmdb"
            if (!normalizedImdb.isNullOrBlank()) keys += "$providerKey|imdb:$normalizedImdb"
            val canonicalShow = toCanonicalTitleKey(normalizedShow)
            if (canonicalShow.isNotBlank()) keys += "$providerKey|title:$canonicalShow"
            return keys.distinct()
        }

        private fun readSeriesBinding(keys: List<String>): Int? {
            if (keys.isEmpty()) return null
            synchronized(seriesBindingLock) {
                keys.forEach { key ->
                    seriesBindingMemory[key]?.let { return it }
                }
            }
            val raw = prefs.getString(seriesBindingPrefKey, null) ?: return null
            val persisted = runCatching { gson.fromJson(raw, ResolverPersistedSeriesBindings::class.java) }.getOrNull() ?: return null
            keys.forEach { key ->
                val seriesId = persisted.items[key] ?: return@forEach
                synchronized(seriesBindingLock) {
                    seriesBindingMemory[key] = seriesId
                }
                return seriesId
            }
            return null
        }

        private fun writeSeriesBinding(keys: List<String>, seriesId: Int) {
            if (keys.isEmpty()) return
            synchronized(seriesBindingLock) {
                keys.forEach { key -> seriesBindingMemory[key] = seriesId }
            }
            val existingRaw = prefs.getString(seriesBindingPrefKey, null)
            val existing = runCatching { gson.fromJson(existingRaw, ResolverPersistedSeriesBindings::class.java) }.getOrNull()
                ?: ResolverPersistedSeriesBindings()
            val merged = LinkedHashMap(existing.items)
            keys.forEach { key -> merged[key] = seriesId }
            while (merged.size > 2048) {
                val oldestKey = merged.keys.firstOrNull() ?: break
                merged.remove(oldestKey)
            }
            runCatching {
                prefs.edit().putString(seriesBindingPrefKey, gson.toJson(ResolverPersistedSeriesBindings(merged))).apply()
            }
        }

        // ========== TASK_17: Public methods for series caching ==========

        /**
         * Load catalog for UI matching (returns simplified entries).
         */
        suspend fun loadCatalogForMatching(
            providerKey: String,
            creds: XtreamCredentials
        ): List<MatchCandidate> {
            val catalog = loadCatalog(providerKey, creds, allowNetwork = true, forceRefresh = false)
            return catalog.entries.map { entry ->
                MatchCandidate(
                    seriesId = entry.seriesId,
                    name = entry.name,
                    normalizedName = entry.normalizedName,
                    canonicalTitleKey = entry.canonicalTitleKey,
                    titleTokens = entry.titleTokens,
                    tmdb = entry.tmdb,
                    imdb = entry.imdb,
                    year = entry.year,
                    cover = null // Cover isn't stored in resolver entries, use series list lookup
                )
            }
        }

        /**
         * Load indexed catalog for O(1) lookups.
         * Returns the full ResolverCatalogIndex with indexed maps for fast matching.
         */
        suspend fun loadCatalogIndexForMatching(
            providerKey: String,
            creds: XtreamCredentials
        ): ResolverCatalogIndex {
            return loadCatalog(providerKey, creds, allowNetwork = true, forceRefresh = false)
        }

        /**
         * Build matching candidates using indexed lookups (O(1) for ID/title matches).
         * Replaces linear scans with hash map lookups for significant performance gains.
         */
        fun buildMatchingCandidatesIndexed(
            catalogIndex: ResolverCatalogIndex,
            normalizedShow: String,
            normalizedTmdb: String?,
            normalizedImdb: String?,
            year: Int?
        ): List<MatchResult> {
            val out = LinkedHashMap<Int, MatchResult>()

            // TMDB match via indexed lookup (O(1))
            if (!normalizedTmdb.isNullOrBlank()) {
                catalogIndex.tmdbMap[normalizedTmdb]?.forEach { entry ->
                    out[entry.seriesId] = MatchResult(
                        seriesId = entry.seriesId,
                        name = entry.name,
                        confidence = 0.98f,
                        method = "tmdb_id",
                        cover = null
                    )
                }
            }

            // IMDB match via indexed lookup (O(1))
            if (!normalizedImdb.isNullOrBlank()) {
                catalogIndex.imdbMap[normalizedImdb]?.forEach { entry ->
                    val prev = out[entry.seriesId]
                    if (prev == null || prev.confidence < 0.99f) {
                        out[entry.seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = 0.99f,
                            method = "imdb_id",
                            cover = null
                        )
                    }
                }
            }

            // Canonical title match via indexed lookup (O(1))
            if (normalizedShow.isNotBlank()) {
                val canonicalShow = toCanonicalTitleKey(normalizedShow)
                if (canonicalShow.isNotBlank()) {
                    catalogIndex.canonicalTitleMap[canonicalShow]?.forEach { entry ->
                        if (out.containsKey(entry.seriesId)) return@forEach
                        val yearDelta = when {
                            year == null || entry.year == null -> 0
                            else -> kotlin.math.abs(year - entry.year)
                        }
                        if (yearDelta > 1) return@forEach
                        val confidence = when (yearDelta) {
                            0 -> 0.93f
                            1 -> 0.90f
                            else -> 0.88f
                        }
                        out[entry.seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = confidence,
                            method = "title_canonical",
                            cover = null
                        )
                    }
                }

                // Token-based matching via indexed intersection (O(k) where k = query tokens)
                val queryTokens = extractTitleTokens(normalizedShow)
                if (queryTokens.isNotEmpty()) {
                    // Get candidate entries by collecting from token map
                    val candidateCounts = mutableMapOf<Int, Pair<ResolverSeriesEntry, Int>>()
                    queryTokens.forEach { token ->
                        catalogIndex.tokenMap[token]?.forEach { entry ->
                            val prev = candidateCounts[entry.seriesId]
                            if (prev == null) {
                                candidateCounts[entry.seriesId] = entry to 1
                            } else {
                                candidateCounts[entry.seriesId] = entry to (prev.second + 1)
                            }
                        }
                    }

                    candidateCounts.forEach { (seriesId, pair) ->
                        if (out.containsKey(seriesId)) return@forEach
                        val (entry, overlap) = pair
                        if (overlap <= 0) return@forEach
                        val coverage = overlap.toFloat() / queryTokens.size.toFloat()
                        val accepted = if (queryTokens.size == 1) {
                            coverage >= 1f
                        } else {
                            overlap >= 2 || coverage >= 0.6f
                        }
                        if (!accepted) return@forEach
                        val yearDelta = when {
                            year == null || entry.year == null -> 0
                            else -> kotlin.math.abs(year - entry.year)
                        }
                        if (yearDelta > 1) return@forEach
                        val yearScore = when (yearDelta) {
                            0 -> 120
                            1 -> 70
                            else -> 35
                        }
                        val total = (coverage * 1_000f).toInt() + (overlap * 180) + yearScore
                        val confidence = when {
                            coverage >= 1f && overlap >= 2 -> 0.86f
                            coverage >= 0.8f -> 0.82f
                            else -> 0.76f
                        }
                        out[seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = confidence,
                            method = "title_tokens",
                            cover = null
                        )
                    }
                }
            }

            return out.values.sortedByDescending { it.confidence }
        }

        /**
         * Build matching candidates by name only using indexed lookups (O(1) for canonical, O(k) for tokens).
         * Simplified matching for faster UI lookups.
         */
        fun buildMatchingCandidatesByNameIndexed(
            catalogIndex: ResolverCatalogIndex,
            normalizedShow: String,
            year: Int?
        ): List<MatchResult> {
            if (normalizedShow.isBlank()) return emptyList()

            val out = LinkedHashMap<Int, MatchResult>()
            val canonicalShow = toCanonicalTitleKey(normalizedShow)

            // Exact canonical match via indexed lookup (O(1))
            if (canonicalShow.isNotBlank()) {
                catalogIndex.canonicalTitleMap[canonicalShow]?.forEach { entry ->
                    val yearDelta = when {
                        year == null || entry.year == null -> 0
                        else -> kotlin.math.abs(year - entry.year)
                    }
                    if (yearDelta <= 2) {
                        val confidence = when (yearDelta) {
                            0 -> 0.95f
                            1 -> 0.92f
                            else -> 0.88f
                        }
                        out[entry.seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = confidence,
                            method = "title_canonical",
                            cover = null
                        )
                    }
                }
            }

            // Token-based matching via indexed intersection (O(k))
            val queryTokens = extractTitleTokens(normalizedShow)
            if (queryTokens.isNotEmpty()) {
                val candidateCounts = mutableMapOf<Int, Pair<ResolverSeriesEntry, Int>>()
                queryTokens.forEach { token ->
                    catalogIndex.tokenMap[token]?.forEach { entry ->
                        val prev = candidateCounts[entry.seriesId]
                        if (prev == null) {
                            candidateCounts[entry.seriesId] = entry to 1
                        } else {
                            candidateCounts[entry.seriesId] = entry to (prev.second + 1)
                        }
                    }
                }

                candidateCounts.forEach { (seriesId, pair) ->
                    // Skip if already matched canonically
                    if (out.containsKey(seriesId)) return@forEach

                    val (entry, overlapSize) = pair
                    if (overlapSize <= 0) return@forEach

                    val candidateTokens = entry.titleTokens
                    val queryCoverage = overlapSize.toFloat() / queryTokens.size.toFloat()
                    val candidateCoverage = overlapSize.toFloat() / candidateTokens.size.toFloat()

                    // Balanced acceptance criteria
                    val accepted = when {
                        queryTokens.size == 1 -> queryCoverage >= 1f
                        queryTokens.size == 2 -> overlapSize >= 2 || (overlapSize >= 1 && queryCoverage >= 0.5f)
                        else -> overlapSize >= 2 && queryCoverage >= 0.4f
                    }
                    if (!accepted) return@forEach

                    // Year matching
                    val yearDelta = when {
                        year == null || entry.year == null -> 0
                        else -> kotlin.math.abs(year - entry.year)
                    }
                    if (yearDelta > 3) return@forEach

                    // Calculate confidence
                    val baseConfidence = when {
                        queryCoverage >= 1f && candidateCoverage >= 0.8f -> 0.88f
                        queryCoverage >= 0.8f && candidateCoverage >= 0.5f -> 0.82f
                        queryCoverage >= 0.5f -> 0.75f
                        else -> 0.70f
                    }
                    val yearAdjust = when (yearDelta) {
                        0 -> 0.02f
                        1 -> 0.01f
                        else -> 0f
                    }
                    val finalConfidence = (baseConfidence + yearAdjust).coerceAtMost(0.90f)

                    out[seriesId] = MatchResult(
                        seriesId = entry.seriesId,
                        name = entry.name,
                        confidence = finalConfidence,
                        method = "title_tokens",
                        cover = null
                    )
                }
            }

            return out.values.sortedByDescending { it.confidence }
        }

        /**
         * Build matching candidates for UI (simplified version of buildCandidates).
         * @deprecated Use buildMatchingCandidatesIndexed for better performance.
         */
        fun buildMatchingCandidates(
            catalog: List<MatchCandidate>,
            normalizedShow: String,
            normalizedTmdb: String?,
            normalizedImdb: String?,
            year: Int?
        ): List<MatchResult> {
            val out = LinkedHashMap<Int, MatchResult>()

            // TMDB match (highest priority)
            if (!normalizedTmdb.isNullOrBlank()) {
                catalog.filter { normalizeTmdbId(it.tmdb) == normalizedTmdb }.forEach { entry ->
                    out[entry.seriesId] = MatchResult(
                        seriesId = entry.seriesId,
                        name = entry.name,
                        confidence = 0.98f,
                        method = "tmdb_id",
                        cover = entry.cover
                    )
                }
            }

            // IMDB match
            if (!normalizedImdb.isNullOrBlank()) {
                catalog.filter { normalizeImdbId(it.imdb) == normalizedImdb }.forEach { entry ->
                    val prev = out[entry.seriesId]
                    if (prev == null || prev.confidence < 0.99f) {
                        out[entry.seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = 0.99f,
                            method = "imdb_id",
                            cover = entry.cover
                        )
                    }
                }
            }

            // Title matching
            if (normalizedShow.isNotBlank()) {
                val canonicalShow = toCanonicalTitleKey(normalizedShow)
                if (canonicalShow.isNotBlank()) {
                    catalog.filter { it.canonicalTitleKey == canonicalShow }.forEach { entry ->
                        val yearDelta = when {
                            year == null || entry.year == null -> 0
                            else -> kotlin.math.abs(year - entry.year)
                        }
                        if (yearDelta <= 1) {
                            val confidence = when (yearDelta) {
                                0 -> 0.93f
                                1 -> 0.90f
                                else -> 0.88f
                            }
                            val existing = out[entry.seriesId]
                            if (existing == null || existing.confidence < confidence) {
                                out[entry.seriesId] = MatchResult(
                                    seriesId = entry.seriesId,
                                    name = entry.name,
                                    confidence = confidence,
                                    method = "title_canonical",
                                    cover = entry.cover
                                )
                            }
                        }
                    }
                }

                // Token-based matching
                val queryTokens = extractTitleTokens(normalizedShow)
                if (queryTokens.isNotEmpty()) {
                    catalog.forEach { entry ->
                        val overlap = entry.titleTokens.intersect(queryTokens).size
                        if (overlap <= 0) return@forEach
                        val coverage = overlap.toFloat() / queryTokens.size.toFloat()
                        val accepted = if (queryTokens.size == 1) coverage >= 1f else (overlap >= 2 || coverage >= 0.6f)
                        if (!accepted) return@forEach
                        val yearDelta = when {
                            year == null || entry.year == null -> 0
                            else -> kotlin.math.abs(year - entry.year)
                        }
                        if (yearDelta > 1) return@forEach
                        val confidence = when {
                            coverage >= 1f && overlap >= 2 -> 0.86f
                            coverage >= 0.8f -> 0.82f
                            else -> 0.76f
                        }
                        val existing = out[entry.seriesId]
                        if (existing == null || existing.confidence < confidence) {
                            out[entry.seriesId] = MatchResult(
                                seriesId = entry.seriesId,
                                name = entry.name,
                                confidence = confidence,
                                method = "title_tokens",
                                cover = entry.cover
                            )
                        }
                    }
                }
            }

            return out.values.sortedByDescending { it.confidence }
        }

        /**
         * Simplified name-only matching for faster UI lookups.
         * Balanced matching to show relevant results while filtering false positives.
         */
        fun buildMatchingCandidatesByName(
            catalog: List<MatchCandidate>,
            normalizedShow: String,
            year: Int?
        ): List<MatchResult> {
            if (normalizedShow.isBlank()) return emptyList()
            
            val out = LinkedHashMap<Int, MatchResult>()
            val canonicalShow = toCanonicalTitleKey(normalizedShow)
            
            // Exact canonical match (highest priority for name matching)
            if (canonicalShow.isNotBlank()) {
                catalog.filter { it.canonicalTitleKey == canonicalShow }.forEach { entry ->
                    val yearDelta = when {
                        year == null || entry.year == null -> 0
                        else -> kotlin.math.abs(year - entry.year)
                    }
                    if (yearDelta <= 2) {
                        val confidence = when (yearDelta) {
                            0 -> 0.95f
                            1 -> 0.92f
                            else -> 0.88f
                        }
                        out[entry.seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = confidence,
                            method = "title_canonical",
                            cover = entry.cover
                        )
                    }
                }
            }

            // Token-based matching with balanced criteria
            val queryTokens = extractTitleTokens(normalizedShow)
            if (queryTokens.isNotEmpty()) {
                catalog.forEach { entry ->
                    // Skip if already matched canonically
                    if (out.containsKey(entry.seriesId)) return@forEach
                    
                    val candidateTokens = entry.titleTokens
                    val overlap = candidateTokens.intersect(queryTokens)
                    val overlapSize = overlap.size
                    
                    if (overlapSize <= 0) return@forEach
                    
                    // Calculate coverage
                    val queryCoverage = overlapSize.toFloat() / queryTokens.size.toFloat()
                    val candidateCoverage = overlapSize.toFloat() / candidateTokens.size.toFloat()
                    
                    // Balanced acceptance criteria:
                    // - Single word: query word must appear in candidate
                    // - Two words: prefer both matching, accept 1 if good coverage
                    // - Multi-word: need at least 2 tokens with decent coverage
                    val accepted = when {
                        queryTokens.size == 1 -> {
                            // Single word: just needs to appear in candidate
                            queryCoverage >= 1f
                        }
                        queryTokens.size == 2 -> {
                            // Two words: both match OR 1 match with >50% query coverage
                            overlapSize >= 2 || (overlapSize >= 1 && queryCoverage >= 0.5f)
                        }
                        else -> {
                            // Three+ words: need at least 2 matches AND >40% query coverage
                            overlapSize >= 2 && queryCoverage >= 0.4f
                        }
                    }
                    
                    if (!accepted) return@forEach
                    
                    // Year matching (optional, don't reject if missing)
                    val yearDelta = when {
                        year == null || entry.year == null -> 0
                        else -> kotlin.math.abs(year - entry.year)
                    }
                    if (yearDelta > 3) return@forEach // More lenient
                    
                    // Calculate confidence based on match quality
                    val baseConfidence = when {
                        // Perfect match (all query tokens + all candidate tokens)
                        queryCoverage >= 1f && candidateCoverage >= 1f -> 0.95f
                        // All query tokens matched
                        queryCoverage >= 1f && candidateCoverage >= 0.8f -> 0.90f
                        queryCoverage >= 1f && candidateCoverage >= 0.5f -> 0.85f
                        queryCoverage >= 1f -> 0.80f
                        // Very high query coverage
                        queryCoverage >= 0.8f && candidateCoverage >= 0.6f -> 0.78f
                        queryCoverage >= 0.8f -> 0.75f
                        // Good coverage
                        queryCoverage >= 0.6f && candidateCoverage >= 0.5f -> 0.72f
                        queryCoverage >= 0.6f -> 0.68f
                        // Moderate coverage
                        else -> 0.65f
                    }
                    
                    // Small penalty for many extra words in candidate (but not too harsh)
                    val extraWords = candidateTokens.size - overlapSize
                    val extraTokensPenalty = when {
                        extraWords > 5 -> 0.08f
                        extraWords > 3 -> 0.04f
                        else -> 0f
                    }
                    
                    val confidence = (baseConfidence - extraTokensPenalty).coerceIn(0.50f, 0.95f)
                    
                    val existing = out[entry.seriesId]
                    if (existing == null || existing.confidence < confidence) {
                        out[entry.seriesId] = MatchResult(
                            seriesId = entry.seriesId,
                            name = entry.name,
                            confidence = confidence,
                            method = "title_tokens",
                            cover = entry.cover
                        )
                    }
                }
            }

            return out.values.sortedByDescending { it.confidence }
        }

        /**
         * Get cached episodes for a series (returns null if not cached).
         */
        fun getCachedEpisodes(providerKey: String, seriesId: Int): List<XtreamSeriesEpisode>? {
            val key = "$providerKey|$seriesId"
            synchronized(seriesInfoLock) {
                val cached = seriesInfoMemory[key]
                if (!cached.isNullOrEmpty()) return cached
            }
            // Try SharedPreferences
            val persisted = runCatching {
                gson.fromJson(
                    prefs.getString(seriesInfoPrefKey(providerKey, seriesId), null),
                    ResolverPersistedSeriesInfo::class.java
                )
            }.getOrNull()
            if (persisted != null && persisted.episodes.isNotEmpty() &&
                System.currentTimeMillis() - persisted.savedAtMs < seriesInfoTtlMs
            ) {
                synchronized(seriesInfoLock) {
                    seriesInfoMemory[key] = persisted.episodes
                }
                return persisted.episodes
            }
            return null
        }

        /**
         * Fetch and cache episodes for a series.
         */
        suspend fun fetchAndCacheEpisodes(
            providerKey: String,
            creds: XtreamCredentials,
            seriesId: Int
        ): List<XtreamSeriesEpisode> {
            return loadSeriesInfo(providerKey, creds, seriesId, allowNetwork = true)
        }

        /**
         * Store TMDB->SeriesID binding for continue watching.
         */
        fun storeSeriesBinding(
            profileId: String,
            tmdbId: Int,
            seriesId: Int,
            seriesName: String
        ) {
            val key = "tmdb_binding:$profileId:$tmdbId"
            val value = "$seriesId|$seriesName|${System.currentTimeMillis()}"
            runCatching {
                prefs.edit().putString(key, value).apply()
            }
        }

        /**
         * Get stored SeriesID binding for a TMDB show.
         */
        fun getSeriesBinding(profileId: String, tmdbId: Int): Triple<Int, String, Long>? {
            val key = "tmdb_binding:$profileId:$tmdbId"
            val value = prefs.getString(key, null) ?: return null
            val parts = value.split("|", limit = 3)
            if (parts.size < 3) return null
            val seriesId = parts[0].toIntOrNull() ?: return null
            val seriesName = parts[1]
            val cachedAt = parts[2].toLongOrNull() ?: 0L
            return Triple(seriesId, seriesName, cachedAt)
        }
    }

    /**
     * Simplified match candidate for UI.
     */
    data class MatchCandidate(
        val seriesId: Int,
        val name: String,
        val normalizedName: String,
        val canonicalTitleKey: String,
        val titleTokens: Set<String>,
        val tmdb: String?,
        val imdb: String?,
        val year: Int?,
        val cover: String?
    )

    /**
     * Match result for UI display.
     */
    data class MatchResult(
        val seriesId: Int,
        val name: String,
        val confidence: Float,
        val method: String,
        val cover: String?
    )

    suspend fun findMovieVodSource(
        title: String,
        year: Int?,
        imdbId: String? = null,
        tmdbId: Int? = null,
        allowNetwork: Boolean = true
    ): List<StreamSource> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()
            val vod = getXtreamVodStreams(creds, allowNetwork, fast = true)
            if (vod.isEmpty()) return@withContext emptyList()

            // Use indexed lookup for O(1) ID matching instead of O(n) filter
            val index = cachedVodIndex
            val indexValid = index != null && index.itemCount == vod.size

            // 1. Try TMDB ID indexed lookup (O(1))
            val normalizedTmdb = normalizeTmdbId(tmdbId)
            if (!normalizedTmdb.isNullOrBlank()) {
                val tmdbMatches = if (indexValid) {
                    index!!.tmdbMap[normalizedTmdb]?.mapNotNull { idx -> vod.getOrNull(idx) } ?: emptyList()
                } else {
                    vod.filter { normalizeTmdbId(it.tmdb) == normalizedTmdb }
                }
                if (tmdbMatches.isNotEmpty()) {
                    return@withContext tmdbMatches.mapNotNull { match ->
                        createMovieStreamSource(match, creds, title, normalizedTmdb, matchScore = 1.0f)
                    }
                }
            }

            // 2. Try IMDB ID indexed lookup (O(1))
            val normalizedImdb = normalizeImdbId(imdbId)
            if (!normalizedImdb.isNullOrBlank()) {
                val imdbMatches = if (indexValid) {
                    index!!.imdbMap[normalizedImdb]?.mapNotNull { idx -> vod.getOrNull(idx) } ?: emptyList()
                } else {
                    vod.filter { normalizeImdbId(it.imdb) == normalizedImdb }
                }
                if (imdbMatches.isNotEmpty()) {
                    return@withContext imdbMatches.mapNotNull { match ->
                        createMovieStreamSource(match, creds, title, normalizedImdb, matchScore = 0.95f)
                    }
                }
            }

            // 3. Fall back to title matching with indexed support
            val normalizedTitle = normalizeLookupText(title)
            if (normalizedTitle.isBlank()) return@withContext emptyList()
            val inputYear = year ?: parseYear(title)

            // Try indexed canonical title lookup first (O(1))
            if (indexValid) {
                val queryTokens = extractTitleTokensFromNormalized(normalizedTitle)
                if (queryTokens.isNotEmpty()) {
                    val canonical = queryTokens.sorted().joinToString(" ")
                    val canonicalMatches = index!!.canonicalTitleMap[canonical]
                        ?.mapNotNull { idx -> vod.getOrNull(idx) }
                        ?.filter { item ->
                            // Year filtering
                            val providerYear = parseYear(item.year ?: item.name.orEmpty())
                            if (inputYear != null && providerYear != null) {
                                kotlin.math.abs(inputYear - providerYear) <= 1
                            } else true
                        }
                        ?.take(10)
                    if (!canonicalMatches.isNullOrEmpty()) {
                        return@withContext canonicalMatches.mapNotNull { match ->
                            createMovieStreamSource(match, creds, title, null, matchScore = 0.85f)
                        }
                    }

                    // Try token-based matching (intersection of token sets)
                    val candidateIndices = queryTokens.flatMap { token ->
                        index.tokenMap[token] ?: emptyList()
                    }.groupingBy { it }.eachCount()
                    
                    val tokenMatches = candidateIndices
                        .filter { (_, count) -> count >= queryTokens.size / 2 || count >= 2 }
                        .keys
                        .mapNotNull { idx -> vod.getOrNull(idx) }
                        .mapNotNull { item ->
                            val name = item.name?.trim().orEmpty()
                            if (name.isBlank()) return@mapNotNull null
                            val score = scoreNameMatch(name, normalizedTitle)
                            if (score <= 0) return@mapNotNull null
                            val providerYear = parseYear(item.year ?: name)
                            val yearDelta = if (inputYear != null && providerYear != null) kotlin.math.abs(providerYear - inputYear) else null
                            val yearAdjust = when {
                                inputYear == null || providerYear == null -> 0
                                yearDelta == 0 -> 20
                                yearDelta == 1 -> 8
                                else -> -25
                            }
                            Pair(item, score + yearAdjust)
                        }
                        .sortedByDescending { it.second }
                        .take(10)

                    if (tokenMatches.isNotEmpty()) {
                        // Convert scores to 0-1 range with max score being 0.75
                        val maxScore = tokenMatches.maxOfOrNull { it.second } ?: 1
                        return@withContext tokenMatches.mapNotNull { (match, rawScore) ->
                            val normalizedScore = if (maxScore > 0) (rawScore.toFloat() / maxScore) * 0.75f else 0.5f
                            createMovieStreamSource(match, creds, title, null, matchScore = normalizedScore)
                        }
                    }
                }
            }

            // 4. Final fallback: linear scan (only if index unavailable or no matches)
            val matches = vod
                .asSequence()
                .mapNotNull { item ->
                    val name = item.name?.trim().orEmpty()
                    if (name.isBlank()) return@mapNotNull null
                    val score = scoreNameMatch(name, normalizedTitle)
                    if (score <= 0) return@mapNotNull null
                    val providerYear = parseYear(item.year ?: name)
                    val yearDelta = if (inputYear != null && providerYear != null) kotlin.math.abs(providerYear - inputYear) else null
                    val yearAdjust = when {
                        inputYear == null || providerYear == null -> 0
                        yearDelta == 0 -> 20
                        yearDelta == 1 -> 8
                        else -> -25
                    }
                    Pair(item, score + yearAdjust)
                }
                .sortedByDescending { it.second }
                .take(10)
                .toList()
            
            // Convert scores to 0-1 range with max score being 0.6 for fallback matches
            val maxScore = matches.maxOfOrNull { it.second } ?: 1
            matches.mapNotNull { (item, rawScore) ->
                val normalizedScore = if (maxScore > 0) (rawScore.toFloat() / maxScore) * 0.6f else 0.3f
                createMovieStreamSource(item, creds, title, null, matchScore = normalizedScore)
            }
        }
    }

    private fun createMovieStreamSource(
        item: XtreamVodStream,
        creds: XtreamCredentials,
        title: String,
        fallbackTitle: String?,
        matchScore: Float = 0f
    ): StreamSource? {
        val streamId = item.streamId ?: return null
        val ext = item.containerExtension?.trim()?.ifBlank { null } ?: "mp4"
        val streamUrl = "${creds.baseUrl}/movie/${creds.username}/${creds.password}/$streamId.$ext"
        return StreamSource(
            source = item.name?.trim().orEmpty().ifBlank { title.ifBlank { fallbackTitle ?: "" } },
            addonName = "IPTV VOD",
            addonId = "iptv_xtream_vod",
            quality = inferQuality(item.name.orEmpty()),
            size = "",
            url = streamUrl,
            matchScore = matchScore
        )
    }

    suspend fun findEpisodeVodSource(
        title: String,
        season: Int,
        episode: Int,
        imdbId: String? = null,
        tmdbId: Int? = null,
        allowNetwork: Boolean = true
    ): List<StreamSource> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()
            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"

            val sources = mutableListOf<StreamSource>()
            val normalizedImdb = normalizeImdbId(imdbId)
            val normalizedTmdb = normalizeTmdbId(tmdbId)

            // FAST PATH: Check if we have a stored series binding first
            // This enables instant lookups when user has already selected an IPTV series
            if (tmdbId != null) {
                val storedBinding = seriesResolver.getSeriesBinding(activeProfileId, tmdbId)
                if (storedBinding != null) {
                    val (seriesId, _, _) = storedBinding
                    val cachedEpisodes = seriesResolver.getCachedEpisodes(providerKey, seriesId)
                    if (!cachedEpisodes.isNullOrEmpty()) {
                        val targetEpisode = cachedEpisodes.firstOrNull { it.season == season && it.episode == episode }
                        if (targetEpisode != null) {
                            val ext = targetEpisode.containerExtension?.trim()?.ifBlank { null } ?: "mp4"
                            val streamUrl = "${creds.baseUrl}/series/${creds.username}/${creds.password}/${targetEpisode.id}.$ext"
                            System.err.println("[VOD] Fast path hit: tmdb=$tmdbId -> series=$seriesId -> S${season}E${episode}")
                            sources.add(
                                StreamSource(
                                    source = "$title S${season}E${episode}",
                                    addonName = "IPTV Series VOD",
                                    addonId = "iptv_xtream_vod",
                                    quality = inferQuality(title),
                                    size = "",
                                    url = streamUrl,
                                    matchScore = 1.0f  // Perfect match via stored binding
                                )
                            )
                            // Don't return early - still fetch catalog fallback for additional series sources
                        }
                    }
                }
            }

            // Skip resolver if fast path already found a source (avoid duplicate lookups)
            if (sources.isEmpty()) {
                // REGULAR PATH: Full series resolution
                val normalizedTitle = normalizeLookupText(title)
                if (normalizedTitle.isBlank() && normalizedImdb.isNullOrBlank() && normalizedTmdb.isNullOrBlank()) {
                    // Still try catalog fallback even without identifiers
                } else {
                    val resolvedSource = seriesResolver.resolveEpisode(
                        providerKey = providerKey,
                        creds = creds,
                        showTitle = title,
                        season = season,
                        episode = episode,
                        tmdbId = tmdbId,
                        imdbId = imdbId,
                        year = parseYear(title),
                        allowNetwork = allowNetwork
                    )

                    resolvedSource?.let { resolved ->
                        val ext = resolved.containerExtension?.trim()?.ifBlank { null } ?: "mp4"
                        val streamUrl = "${creds.baseUrl}/series/${creds.username}/${creds.password}/${resolved.streamId}.$ext"
                        sources.add(
                            StreamSource(
                                source = "$title S${season}E${episode}",
                                addonName = "IPTV Series VOD",
                                addonId = "iptv_xtream_vod",
                                quality = inferQuality(title),
                                size = "",
                                url = streamUrl,
                                matchScore = resolved.confidence  // Use resolver confidence score
                            )
                        )
                        
                        // Store series binding for fast lookups next time
                        if (tmdbId != null) {
                            seriesResolver.storeSeriesBinding(activeProfileId, tmdbId, resolved.seriesId, title)
                        }
                    }
                }
            }

            // Always fetch catalog fallback to get episode series sources
            val catalogSources = findEpisodeVodFromVodCatalogFallback(
                creds = creds,
                title = title,
                season = season,
                episode = episode,
                normalizedImdb = normalizedImdb,
                normalizedTmdb = normalizedTmdb,
                allowNetwork = allowNetwork
            )

            (sources + catalogSources).distinctBy { it.url }
        }
    }

    private suspend fun findEpisodeVodFromVodCatalogFallback(
        creds: XtreamCredentials,
        title: String,
        season: Int,
        episode: Int,
        normalizedImdb: String?,
        normalizedTmdb: String?,
        allowNetwork: Boolean
    ): List<StreamSource> {
        val normalizedTitle = normalizeLookupText(title)
        val seriesList = getXtreamSeriesList(creds, allowNetwork = allowNetwork, fast = true)
        if (seriesList.isEmpty()) return emptyList()

        val matchingSeries = seriesList.asSequence()
            .mapNotNull { item ->
                val seriesId = item.seriesId ?: return@mapNotNull null
                val name = item.name?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null

                val imdbScore = if (!normalizedImdb.isNullOrBlank() && normalizeImdbId(item.imdb) == normalizedImdb) 10_000 else 0
                val tmdbScore = if (!normalizedTmdb.isNullOrBlank() && normalizeTmdbId(item.tmdb) == normalizedTmdb) 9_500 else 0
                val titleScore = if (normalizedTitle.isNotBlank()) {
                    maxOf(scoreNameMatch(name, normalizedTitle), looseSeriesTitleScore(name, normalizedTitle))
                } else {
                    0
                }
                if (imdbScore == 0 && tmdbScore == 0 && titleScore <= 0) return@mapNotNull null
                Triple(item, seriesId, imdbScore + tmdbScore + titleScore)
            }
            .sortedByDescending { it.third }
            .take(10)
            .toList()

        if (matchingSeries.isEmpty()) return emptyList()

        val sources = mutableListOf<StreamSource>()
        val maxScore = matchingSeries.maxOfOrNull { it.third } ?: 1
        for ((seriesItem, seriesId, rawScore) in matchingSeries) {
            val episodes = getXtreamSeriesEpisodes(creds, seriesId, allowNetwork = allowNetwork, fast = true)
            val matchingEpisode = episodes.find { it.season == season && it.episode == episode }
            if (matchingEpisode != null) {
                val ext = matchingEpisode.containerExtension?.trim()?.ifBlank { null } ?: "mp4"
                val streamUrl = "${creds.baseUrl}/series/${creds.username}/${creds.password}/${matchingEpisode.id}.$ext"
                // Normalize score: ID matches get higher score, title matches get lower
                val normalizedScore = when {
                    rawScore >= 9_500 -> 0.95f  // TMDB match
                    rawScore >= 10_000 -> 1.0f  // IMDB match
                    else -> (rawScore.toFloat() / maxScore.coerceAtLeast(1)) * 0.7f  // Title match scaled to max 0.7
                }
                sources.add(
                    StreamSource(
                        source = seriesItem.name?.trim().orEmpty().ifBlank { "$title S${season}E${episode}" },
                        addonName = "IPTV Episode Series",
                        addonId = "iptv_xtream_series",
                        quality = inferQuality(seriesItem.name.orEmpty()),
                        size = "",
                        url = streamUrl,
                        matchScore = normalizedScore
                    )
                )
            }
        }

        return sources.distinctBy { it.url }
    }

    /**
     * Check if VOD cache refresh is needed based on last refresh time and configured interval.
     * Returns true if enough time has elapsed since last refresh.
     */
    suspend fun isVodCacheRefreshNeeded(): Boolean {
        val interval = observeRefreshInterval().first()
        if (interval == IptvRefreshInterval.DISABLED) return false
        
        val lastRefresh = observeLastRefreshTime().first() ?: 0L
        val elapsedMs = System.currentTimeMillis() - lastRefresh
        val intervalMs = interval.hours * 60 * 60 * 1000L
        
        return elapsedMs >= intervalMs
    }

    /**
     * Warm up VOD caches only if refresh is needed based on configured interval.
     * Skips refresh if within the configured auto-refresh window.
     */
    suspend fun warmXtreamVodCachesIfPossible() {
        warmXtreamVodCachesInternal(forceRefresh = false)
    }

    /**
     * Force warm up VOD caches regardless of refresh interval (for manual refresh).
     */
    suspend fun forceWarmXtreamVodCaches() {
        warmXtreamVodCachesInternal(forceRefresh = true)
    }

    private suspend fun warmXtreamVodCachesInternal(forceRefresh: Boolean) {
        withContext(Dispatchers.IO) {
            // Check if refresh is needed based on interval (skip check if force refresh)
            if (!forceRefresh && !isVodCacheRefreshNeeded()) {
                System.err.println("[VOD] Skipping warmup - within refresh interval window")
                return@withContext
            }
            
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext
            runCatching {
                loadXtreamVodStreams(creds)
                loadXtreamSeriesList(creds)
                val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
                val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"
                seriesResolver.refreshCatalog(providerKey, creds)
                // Update last refresh time after successful warmup
                setLastRefreshTime(System.currentTimeMillis())
                System.err.println("[VOD] Cache warmup completed, updated last refresh time")
            }
        }
    }

    suspend fun getVodCategories(): List<XtreamVodCategory> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()

            val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_vod_categories"
            val categories: List<XtreamVodCategory> = requestJson(
                url,
                object : TypeToken<List<XtreamVodCategory>>() {}.type
            ) ?: emptyList()
            categories
        }
    }
    
    /**
     * Get cached VOD categories (populated during refresh).
     * Returns empty list if not yet cached.
     */
    suspend fun getCachedVodCategories(): List<XtreamVodCategory> {
        return withContext(Dispatchers.Default) {
            cachedVodCategories
        }
    }

    suspend fun getSeriesCategories(): List<XtreamSeriesCategory> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()

            val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series_categories"
            val categories: List<XtreamSeriesCategory> = requestJson(
                url,
                object : TypeToken<List<XtreamSeriesCategory>>() {}.type
            ) ?: emptyList()
            categories
        }
    }
    
    /**
     * Get cached Series categories (populated during refresh).
     * Returns empty list if not yet cached.
     */
    suspend fun getCachedSeriesCategories(): List<XtreamSeriesCategory> {
        return withContext(Dispatchers.Default) {
            cachedSeriesCategories
        }
    }

    suspend fun getMoviesByCategory(categoryId: String): List<XtreamVodStream> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()

            // First ensure we have all VOD streams loaded
            val allVod = getXtreamVodStreams(creds, allowNetwork = true)
            // Filter by category
            allVod.filter { it.categoryId == categoryId }
        }
    }

    suspend fun getSeriesByCategory(categoryId: String): List<XtreamSeriesItem> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()

            val allSeries = getXtreamSeriesList(creds, allowNetwork = true)
            allSeries.filter { series -> series.categoryId == categoryId }
        }
    }

    suspend fun getVodStreamUrl(streamId: Int, extension: String): String? {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            "${creds.baseUrl}/movie/${creds.username}/${creds.password}/$streamId.$extension"
        }
    }

    suspend fun getSeriesEpisodeUrl(episodeId: Int): String? {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            "${creds.baseUrl}/series/${creds.username}/${creds.password}/$episodeId.mp4"
        }
    }

    /** Get all VOD streams (movies) grouped by category */
    suspend fun getVodStreamsByCategory(): Map<String, List<XtreamVodStream>> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyMap()

            val allVod = getXtreamVodStreams(creds, allowNetwork = true)
            allVod.groupBy { it.categoryId ?: "uncategorized" }
        }
    }

    /** Get detailed VOD info including tmdb_id */
    suspend fun getVodInfo(vodId: Int): XtreamVodInfo? {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_vod_info&vod_id=$vodId"
            requestJson(url, XtreamVodInfo::class.java)
        }
    }

    /** Get all series grouped by category */
    suspend fun getSeriesByCategoryMap(): Map<String, List<XtreamSeriesItem>> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyMap()

            val allSeries = getXtreamSeriesList(creds, allowNetwork = true)
            allSeries.groupBy { it.categoryId ?: "uncategorized" }
        }
    }

    /** Convert XtreamVodStream to MediaItem */
    fun convertVodStreamToMediaItem(stream: XtreamVodStream): MediaItem? {
        val name = stream.name ?: return null
        val streamId = stream.streamId ?: return null

        return MediaItem(
            id = streamId,
            title = name,
            mediaType = MediaType.MOVIE,
            image = stream.streamIcon ?: "",
            year = stream.year ?: "",
            tmdbRating = stream.tmdb ?: "",
            imdbRating = stream.imdb ?: "",
            status = "iptv:$streamId",
            iptvMovieId = streamId.toString()
        )
    }

    /** Convert XtreamSeriesItem to MediaItem */
    fun convertSeriesItemToMediaItem(series: XtreamSeriesItem): MediaItem? {
        val name = series.name ?: return null
        val seriesId = series.seriesId ?: return null

        return MediaItem(
            id = seriesId,
            title = name,
            mediaType = MediaType.TV,
            image = series.cover ?: series.backdrop ?: "",
            tmdbRating = series.tmdb ?: "",
            imdbRating = series.imdb ?: "",
            status = "iptv_series:$seriesId",
            iptvSeriesId = seriesId.toString()
        )
    }

    suspend fun prefetchEpisodeVodResolution(
        title: String,
        season: Int,
        episode: Int,
        imdbId: String? = null,
        tmdbId: Int? = null
    ) {
        withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext
            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"
            runCatching {
                seriesResolver.resolveEpisode(
                    providerKey = providerKey,
                    creds = creds,
                    showTitle = title,
                    season = season,
                    episode = episode,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    year = parseYear(title),
                    allowNetwork = true
                )
            }
        }
    }

    suspend fun prefetchSeriesInfoForShow(
        title: String,
        imdbId: String? = null,
        tmdbId: Int? = null
    ) {
        withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext
            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"
            runCatching {
                seriesResolver.prefetchSeriesInfo(
                    providerKey = providerKey,
                    creds = creds,
                    showTitle = title,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    year = parseYear(title)
                )
            }
        }
    }

    // ========== TASK_17: Series Caching for Instant Next Episode Playback ==========

    /**
     * Find IPTV series matches for a TV show.
     * Returns a list of potential IPTV series that match the given title/IDs.
     * Used by DetailsScreen to show IPTV series options in stream selector.
     * Uses indexed lookups for O(1) ID matching and O(k) token matching.
     */
    suspend fun findSeriesMatches(
        title: String,
        tmdbId: Int? = null,
        imdbId: String? = null
    ): List<com.arflix.tv.data.model.IptvSeriesMatch> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()

            val normalizedTitle = normalizeLookupText(title)
            if (normalizedTitle.isBlank() && tmdbId == null && imdbId == null) {
                return@withContext emptyList()
            }

            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"

            // Load the indexed catalog (uses disk cache if available)
            val catalogIndex = seriesResolver.loadCatalogIndexForMatching(providerKey, creds)
            if (catalogIndex.entries.isEmpty()) return@withContext emptyList()

            // Use indexed matching for O(1) ID lookups and O(k) token matching
            val candidates = if (tmdbId != null || imdbId != null) {
                // Full matching with IDs using indexed lookups
                seriesResolver.buildMatchingCandidatesIndexed(
                    catalogIndex = catalogIndex,
                    normalizedShow = normalizedTitle,
                    normalizedTmdb = normalizeTmdbId(tmdbId),
                    normalizedImdb = normalizeImdbId(imdbId),
                    year = parseYear(title)
                )
            } else {
                // Simplified name-only matching using indexed lookups
                seriesResolver.buildMatchingCandidatesByNameIndexed(
                    catalogIndex = catalogIndex,
                    normalizedShow = normalizedTitle,
                    year = parseYear(title)
                )
            }

            // Convert to IptvSeriesMatch for UI
            candidates.take(5).map { candidate ->
                com.arflix.tv.data.model.IptvSeriesMatch(
                    seriesId = candidate.seriesId,
                    seriesName = candidate.name,
                    providerName = "IPTV",
                    confidence = candidate.confidence,
                    matchMethod = candidate.method,
                    episodeCount = 0, // Will be populated when user selects
                    coverUrl = candidate.cover
                )
            }
        }
    }

    /**
     * Get cached series episodes if available.
     * Returns null if not cached (caller should fetch via selectSeriesMatch).
     */
    suspend fun getCachedSeriesEpisodes(seriesId: Int): List<com.arflix.tv.data.model.IptvSeriesEpisodeInfo>? {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"

            val episodes = seriesResolver.getCachedEpisodes(providerKey, seriesId)
            if (episodes.isNullOrEmpty()) return@withContext null

            episodes.map { ep ->
                com.arflix.tv.data.model.IptvSeriesEpisodeInfo(
                    seriesId = seriesId,
                    season = ep.season,
                    episode = ep.episode,
                    streamId = ep.id,
                    containerExtension = ep.containerExtension,
                    title = ep.title
                )
            }
        }
    }

    /**
     * Fetch full series info from IPTV provider (including metadata, seasons, episodes).
     * Used when navigating from Series page to populate DetailsScreen directly.
     */
    suspend fun getSeriesFullInfo(seriesId: Int): com.arflix.tv.data.model.IptvSeriesFullInfo? {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series_info&series_id=$seriesId"
            val json: JsonObject = requestJson(url, JsonObject::class.java, client = iptvHttpClient)
                ?: return@withContext null

            parseSeriesFullInfo(seriesId, json, creds)
        }
    }

    private fun parseSeriesFullInfo(
        seriesId: Int,
        json: JsonObject,
        creds: XtreamCredentials
    ): com.arflix.tv.data.model.IptvSeriesFullInfo? {
        return runCatching {
            // Parse "info" section
        val info = json.getAsJsonObject("info") ?: return null
        val name = info.get("name")?.asString ?: return null

        val plot = info.get("plot")?.asString ?: info.get("description")?.asString
        val cast = info.get("cast")?.asString
        val director = info.get("director")?.asString
        val genre = info.get("genre")?.asString
        val releaseDate = info.get("releaseDate")?.asString ?: info.get("release_date")?.asString
        val rating = info.get("rating")?.asString ?: info.get("rating_5based")?.asString
        val coverUrl = info.get("cover")?.asString
        val youtubeTrailer = info.get("youtube_trailer")?.asString

        // Parse backdrop - can be array or single string
        val backdropUrl = when {
            info.has("backdrop_path") -> {
                val backdrop = info.get("backdrop_path")
                when {
                    backdrop?.isJsonArray == true -> backdrop.asJsonArray.firstOrNull()?.asString
                    else -> backdrop?.asString
                }
            }
            else -> null
        }

        // Parse "seasons" section
        val seasonsArray = json.getAsJsonArray("seasons")
        val seasons = seasonsArray?.mapNotNull { seasonElement ->
            val season = seasonElement?.asJsonObject ?: return@mapNotNull null
            // Use parseFlexibleInt to handle both string and numeric season_number values
            val seasonNumber = parseFlexibleInt(season.get("season_number")) ?: return@mapNotNull null
            com.arflix.tv.data.model.IptvSeasonInfo(
                seasonNumber = seasonNumber,
                name = season.get("name")?.asString ?: "Season $seasonNumber",
                overview = season.get("overview")?.asString,
                episodeCount = parseFlexibleInt(season.get("episode_count")) ?: 0,
                coverUrl = season.get("cover")?.asString ?: season.get("cover_big")?.asString,
                airDate = season.get("air_date")?.asString,
                voteAverage = season.get("vote_average")?.asFloat ?: 0f
            )
        } ?: emptyList()

        // Parse "episodes" section
        val episodesElement = json.get("episodes")
        val episodeObjects = mutableListOf<Pair<JsonObject, Int?>>()
        collectXtreamEpisodeObjects(episodesElement, seasonHint = null, out = episodeObjects)

        val episodes = episodeObjects.mapNotNull { (epObj, seasonHint) ->
            val streamId = parseFlexibleInt(epObj.get("id")) ?: return@mapNotNull null
            val episodeNum = parseFlexibleInt(epObj.get("episode_num"))
                ?: parseFlexibleInt(epObj.get("episode_number"))
                ?: parseFlexibleInt(epObj.get("episode"))
                ?: return@mapNotNull null
            val seasonNum = seasonHint
                ?: parseFlexibleInt(epObj.get("season"))
                ?: parseFlexibleInt(epObj.get("season_number"))
                ?: 1

            val epInfo = epObj.getAsJsonObject("info")
            val epTitle = epObj.get("title")?.asString ?: "Episode $episodeNum"
            val epPlot = epInfo?.get("plot")?.asString ?: epInfo?.get("description")?.asString
            val epReleaseDate = epInfo?.get("releasedate")?.asString ?: epInfo?.get("release_date")?.asString
            val epDuration = epInfo?.get("duration")?.asString
            val epStillPath = epInfo?.get("movie_image")?.asString ?: epInfo?.get("image")?.asString
            // Use safe float parsing for rating (handles string numbers like "8.5")
            val epRating = runCatching { 
                epInfo?.get("rating")?.asFloat ?: 0f 
            }.getOrElse { 
                epInfo?.get("rating")?.asString?.toFloatOrNull() ?: 0f 
            }

            com.arflix.tv.data.model.IptvEpisodeInfo(
                streamId = streamId,
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                title = epTitle,
                plot = epPlot,
                releaseDate = epReleaseDate,
                duration = epDuration,
                stillPath = epStillPath,
                containerExtension = epObj.get("container_extension")?.asString,
                rating = epRating
            )
        }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

            val result = com.arflix.tv.data.model.IptvSeriesFullInfo(
                seriesId = seriesId,
                name = name,
                plot = plot,
                cast = cast,
                director = director,
                genre = genre,
                releaseDate = releaseDate,
                rating = rating,
                coverUrl = coverUrl,
                backdropUrl = backdropUrl,
                youtubeTrailer = youtubeTrailer,
                seasons = seasons,
                episodes = episodes
            )
            
            // Log parsing results for debugging corner cases
            System.err.println("[IptvRepository] Parsed series_info for $seriesId: ${seasons.size} seasons, ${episodes.size} episodes")
            
            result
        }.onFailure { e ->
            System.err.println("[IptvRepository] Error parsing series_info for $seriesId: ${e.message}")
            e.printStackTrace()
        }.getOrNull()
    }

    /**
     * Fetch and cache series episodes for the selected series.
     * Call this when user selects an IPTV series match.
     */
    suspend fun fetchAndCacheSeriesEpisodes(seriesId: Int): List<com.arflix.tv.data.model.IptvSeriesEpisodeInfo> {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext emptyList()

            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"

            val episodes = seriesResolver.fetchAndCacheEpisodes(providerKey, creds, seriesId)
            if (episodes.isEmpty()) return@withContext emptyList()

            episodes.map { ep ->
                com.arflix.tv.data.model.IptvSeriesEpisodeInfo(
                    seriesId = seriesId,
                    season = ep.season,
                    episode = ep.episode,
                    streamId = ep.id,
                    containerExtension = ep.containerExtension,
                    title = ep.title
                )
            }
        }
    }

    /**
     * Build episode stream URL directly from cached series info.
     * Returns null if series is not cached or episode not found.
     * This enables instant next episode playback without API calls.
     */
    suspend fun buildEpisodeUrlFromCache(
        seriesId: Int,
        season: Int,
        episode: Int
    ): String? {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext null

            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"

            val episodes = seriesResolver.getCachedEpisodes(providerKey, seriesId)
                ?: return@withContext null

            val targetEpisode = episodes.firstOrNull { it.season == season && it.episode == episode }
                ?: return@withContext null

            val ext = targetEpisode.containerExtension?.trim()?.ifBlank { null } ?: "mp4"
            "${creds.baseUrl}/series/${creds.username}/${creds.password}/${targetEpisode.id}.$ext"
        }
    }

    /**
     * Check if a series has cached episode info (for determining if fast path is available).
     */
    suspend fun hasSeriesEpisodeCache(seriesId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val config = observeConfig().first()
            val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl)
                ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
                ?: return@withContext false

            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            val providerKey = "$activeProfileId|${xtreamCacheKey(creds)}"

            !seriesResolver.getCachedEpisodes(providerKey, seriesId).isNullOrEmpty()
        }
    }

    /**
     * Store series context for continue watching (enables instant next episode).
     */
    suspend fun storeSeriesContext(
        tmdbId: Int,
        seriesId: Int,
        seriesName: String
    ) {
        withContext(Dispatchers.IO) {
            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            seriesResolver.storeSeriesBinding(activeProfileId, tmdbId, seriesId, seriesName)
        }
    }

    /**
     * Get stored series context for a TMDB show.
     * Used to enable instant next episode playback from continue watching.
     */
    suspend fun getStoredSeriesContext(tmdbId: Int): com.arflix.tv.data.model.IptvSeriesContext? {
        return withContext(Dispatchers.IO) {
            val activeProfileId = runCatching { profileManager.activeProfileId.first() }.getOrDefault("default")
            seriesResolver.getSeriesBinding(activeProfileId, tmdbId)?.let { (seriesId, seriesName, cachedAt) ->
                com.arflix.tv.data.model.IptvSeriesContext(
                    seriesId = seriesId,
                    seriesName = seriesName,
                    cachedAtMs = cachedAt
                )
            }
        }
    }


    private fun xtreamCacheKey(creds: XtreamCredentials): String {
        return "${creds.baseUrl}|${creds.username}|${creds.password}"
    }

    private fun ensureXtreamVodCacheOwnership(creds: XtreamCredentials) {
        val key = xtreamCacheKey(creds)
        if (xtreamVodCacheKey == key) return
        xtreamVodCacheKey = key
        xtreamVodLoadedAtMs = 0L
        xtreamSeriesLoadedAtMs = 0L
        cachedXtreamVodStreams = emptyList()
        cachedVodIndex = null
        cachedXtreamSeries = emptyList()
        cachedXtreamSeriesEpisodes = emptyMap()
        xtreamSeriesEpisodeInFlight = emptyMap()
    }

    // ── Disk cache helpers for Xtream VOD / Series catalogs ──────────────
    // Persists catalogs to JSON files so they survive app restarts, avoiding
    // 15-28 s re-downloads on cold start for large providers.

    private data class XtreamDiskCache<T>(val savedAtMs: Long, val items: List<T>)

    private fun xtreamDiskCacheDir(): File = File(context.filesDir, "xtream_vod_disk_cache").also { it.mkdirs() }

    private fun xtreamDiskCacheHash(creds: XtreamCredentials): String {
        val raw = "${creds.baseUrl}|${creds.username}|${creds.password}"
        return MessageDigest.getInstance("MD5").digest(raw.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun vodDiskCacheFile(creds: XtreamCredentials): File =
        File(xtreamDiskCacheDir(), "vod_${xtreamDiskCacheHash(creds)}.json")

    private fun seriesDiskCacheFile(creds: XtreamCredentials): File =
        File(xtreamDiskCacheDir(), "series_${xtreamDiskCacheHash(creds)}.json")

    private fun <T> readDiskCache(file: File, type: Type): XtreamDiskCache<T>? {
        val parentExists = file.parentFile?.exists() == true
        val parentFiles = runCatching { file.parentFile?.listFiles()?.map { it.name } }.getOrNull()
        if (!file.exists()) {
            System.err.println("[VOD-Cache] Disk cache file not found: ${file.absolutePath} (parent exists=$parentExists, parent files=$parentFiles)")
            return null
        }
        val sizeKb = file.length() / 1024
        System.err.println("[VOD-Cache] Reading disk cache: ${file.name} (${sizeKb}KB)")
        return runCatching {
            file.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                gson.fromJson<XtreamDiskCache<T>>(reader, type)
            }
        }.onFailure { e ->
            System.err.println("[VOD-Cache] Failed to read disk cache: ${file.name}: ${e.message}")
        }.getOrNull()
    }

    private fun <T> writeDiskCache(file: File, savedAtMs: Long, items: List<T>) {
        runCatching {
            java.io.FileOutputStream(file).use { fos ->
                java.io.BufferedWriter(java.io.OutputStreamWriter(fos, StandardCharsets.UTF_8)).use { writer ->
                    gson.toJson(XtreamDiskCache(savedAtMs, items), writer)
                    writer.flush()
                }
                runCatching { fos.fd.sync() }  // Best-effort flush to disk; may fail on emulator
            }
            System.err.println("[VOD-Cache] Wrote disk cache: ${file.name} (${file.length() / 1024}KB), exists=${file.exists()}")
        }.onFailure { e ->
            System.err.println("[VOD-Cache] Failed to write disk cache: ${file.name}: ${e.message}")
        }
    }

    private val vodDiskCacheType: Type by lazy {
        object : TypeToken<XtreamDiskCache<XtreamVodStream>>() {}.type
    }
    private val seriesDiskCacheType: Type by lazy {
        object : TypeToken<XtreamDiskCache<XtreamSeriesItem>>() {}.type
    }

    // ── VOD Catalog Index Builder ─────────────────────────────────────────
    // Builds indexed maps for O(1) lookups instead of O(n) linear scans.
    // Memory-efficient: stores list indices (Int) instead of copying items.

    private fun buildVodCatalogIndex(vod: List<XtreamVodStream>): VodCatalogIndex {
        val startTime = System.currentTimeMillis()
        val tmdbMap = mutableMapOf<String, MutableList<Int>>()
        val imdbMap = mutableMapOf<String, MutableList<Int>>()
        val canonicalTitleMap = mutableMapOf<String, MutableList<Int>>()
        val tokenMap = mutableMapOf<String, MutableList<Int>>()

        vod.forEachIndexed { index, item ->
            // Index by TMDB ID
            normalizeTmdbId(item.tmdb)?.let { tmdbId ->
                tmdbMap.getOrPut(tmdbId) { mutableListOf() }.add(index)
            }
            // Index by IMDB ID
            normalizeImdbId(item.imdb)?.let { imdbId ->
                imdbMap.getOrPut(imdbId) { mutableListOf() }.add(index)
            }
            // Index by title tokens
            val name = item.name?.trim().orEmpty()
            if (name.isNotBlank()) {
                val normalized = normalizeLookupText(name)
                val tokens = extractTitleTokensFromNormalized(normalized)
                if (tokens.isNotEmpty()) {
                    // Canonical title = sorted tokens joined
                    val canonical = tokens.sorted().joinToString(" ")
                    canonicalTitleMap.getOrPut(canonical) { mutableListOf() }.add(index)
                    // Individual tokens
                    tokens.forEach { token ->
                        tokenMap.getOrPut(token) { mutableListOf() }.add(index)
                    }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        System.err.println("[VOD-Index] Built index for ${vod.size} items in ${elapsed}ms: tmdb=${tmdbMap.size}, imdb=${imdbMap.size}, canonical=${canonicalTitleMap.size}, tokens=${tokenMap.size}")

        return VodCatalogIndex(
            createdAtMs = System.currentTimeMillis(),
            itemCount = vod.size,
            tmdbMap = tmdbMap,
            imdbMap = imdbMap,
            canonicalTitleMap = canonicalTitleMap,
            tokenMap = tokenMap
        )
    }

    // ── Restructured load methods: disk cache + non-blocking network ─────

    private suspend fun loadXtreamVodStreams(
        creds: XtreamCredentials,
        fast: Boolean = false
    ): List<XtreamVodStream> {
        return withContext(Dispatchers.IO) {
            // 1. Fast check: in-memory cache (no lock needed, fields are @Volatile)
            ensureXtreamVodCacheOwnership(creds)
            val now = System.currentTimeMillis()
            if (cachedXtreamVodStreams.isNotEmpty() && now - xtreamVodLoadedAtMs < xtreamVodCacheMs) {
                return@withContext cachedXtreamVodStreams
            }

            // 2. Check disk cache (fast — reading a file, not a network call)
            val diskFile = vodDiskCacheFile(creds)
            val diskCache: XtreamDiskCache<XtreamVodStream>? = readDiskCache(diskFile, vodDiskCacheType)
            if (diskCache != null && diskCache.items.isNotEmpty() && now - diskCache.savedAtMs < xtreamVodCacheMs) {
                System.err.println("[VOD-Cache] Loaded ${diskCache.items.size} VOD streams from disk cache (age ${(now - diskCache.savedAtMs) / 1000}s)")
                cachedXtreamVodStreams = diskCache.items
                xtreamVodLoadedAtMs = diskCache.savedAtMs
                // Build index for fast lookups
                cachedVodIndex = buildVodCatalogIndex(diskCache.items)
                return@withContext diskCache.items
            }

            // 3. Download from network — NO mutex held during download
            System.err.println("[VOD-Cache] Downloading VOD streams from network...")
            val downloadStart = System.currentTimeMillis()
            val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_vod_streams"
            val vod: List<XtreamVodStream> =
                requestJson(
                    url,
                    object : TypeToken<List<XtreamVodStream>>() {}.type,
                    client = if (fast) xtreamLookupHttpClient else iptvHttpClient
                ) ?: emptyList()
            val elapsed = System.currentTimeMillis() - downloadStart
            System.err.println("[VOD-Cache] Downloaded ${vod.size} VOD streams in ${elapsed}ms")

            // 4. Swap into memory cache
            if (vod.isNotEmpty()) {
                val writeTime = System.currentTimeMillis()
                cachedXtreamVodStreams = vod
                xtreamVodLoadedAtMs = writeTime
                // Build index for fast lookups
                cachedVodIndex = buildVodCatalogIndex(vod)
                // 5. Persist to disk in background
                runCatching { writeDiskCache(diskFile, writeTime, vod) }
                System.err.println("[VOD-Cache] Saved VOD streams to disk cache")
            } else if (diskCache != null && diskCache.items.isNotEmpty()) {
                // Network returned empty — use stale disk cache
                System.err.println("[VOD-Cache] Network returned empty, using stale disk cache (${diskCache.items.size} items)")
                cachedXtreamVodStreams = diskCache.items
                xtreamVodLoadedAtMs = diskCache.savedAtMs
                // Build index for fast lookups
                cachedVodIndex = buildVodCatalogIndex(diskCache.items)
                return@withContext diskCache.items
            }

            vod
        }
    }

    private suspend fun getXtreamVodStreams(
        creds: XtreamCredentials,
        allowNetwork: Boolean,
        fast: Boolean = false
    ): List<XtreamVodStream> {
        if (allowNetwork) return loadXtreamVodStreams(creds, fast = fast)
        // If no network allowed, try memory first, then disk
        ensureXtreamVodCacheOwnership(creds)
        if (cachedXtreamVodStreams.isNotEmpty()) return cachedXtreamVodStreams
        // Try disk cache even when network not allowed
        return withContext(Dispatchers.IO) {
            val diskCache: XtreamDiskCache<XtreamVodStream>? = readDiskCache(vodDiskCacheFile(creds), vodDiskCacheType)
            if (diskCache != null && diskCache.items.isNotEmpty()) {
                cachedXtreamVodStreams = diskCache.items
                xtreamVodLoadedAtMs = diskCache.savedAtMs
                // Build index for fast lookups
                cachedVodIndex = buildVodCatalogIndex(diskCache.items)
                diskCache.items
            } else {
                emptyList()
            }
        }
    }

    private suspend fun loadXtreamSeriesList(
        creds: XtreamCredentials,
        fast: Boolean = false
    ): List<XtreamSeriesItem> {
        return withContext(Dispatchers.IO) {
            // 1. Fast check: in-memory cache
            ensureXtreamVodCacheOwnership(creds)
            val now = System.currentTimeMillis()
            if (cachedXtreamSeries.isNotEmpty() && now - xtreamSeriesLoadedAtMs < xtreamVodCacheMs) {
                return@withContext cachedXtreamSeries
            }

            // 2. Check disk cache
            val diskFile = seriesDiskCacheFile(creds)
            val diskCache: XtreamDiskCache<XtreamSeriesItem>? = readDiskCache(diskFile, seriesDiskCacheType)
            if (diskCache != null && diskCache.items.isNotEmpty() && now - diskCache.savedAtMs < xtreamVodCacheMs) {
                System.err.println("[VOD-Cache] Loaded ${diskCache.items.size} series from disk cache (age ${(now - diskCache.savedAtMs) / 1000}s)")
                cachedXtreamSeries = diskCache.items
                xtreamSeriesLoadedAtMs = diskCache.savedAtMs
                return@withContext diskCache.items
            }

            // 3. Download from network — NO mutex held
            System.err.println("[VOD-Cache] Downloading series list from network...")
            val downloadStart = System.currentTimeMillis()
            val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series"
            val series: List<XtreamSeriesItem> =
                requestJson(
                    url,
                    object : TypeToken<List<XtreamSeriesItem>>() {}.type,
                    client = if (fast) xtreamLookupHttpClient else iptvHttpClient
                ) ?: emptyList()
            val elapsed = System.currentTimeMillis() - downloadStart
            System.err.println("[VOD-Cache] Downloaded ${series.size} series in ${elapsed}ms")

            // 4. Swap into memory cache
            if (series.isNotEmpty()) {
                val writeTime = System.currentTimeMillis()
                cachedXtreamSeries = series
                xtreamSeriesLoadedAtMs = writeTime
                // 5. Persist to disk
                runCatching { writeDiskCache(diskFile, writeTime, series) }
                System.err.println("[VOD-Cache] Saved series list to disk cache")
            } else if (diskCache != null && diskCache.items.isNotEmpty()) {
                System.err.println("[VOD-Cache] Network returned empty, using stale disk cache (${diskCache.items.size} items)")
                cachedXtreamSeries = diskCache.items
                xtreamSeriesLoadedAtMs = diskCache.savedAtMs
                return@withContext diskCache.items
            }

            series
        }
    }

    private suspend fun getXtreamSeriesList(
        creds: XtreamCredentials,
        allowNetwork: Boolean,
        fast: Boolean = false
    ): List<XtreamSeriesItem> {
        if (allowNetwork) return loadXtreamSeriesList(creds, fast = fast)
        // If no network allowed, try memory first, then disk
        ensureXtreamVodCacheOwnership(creds)
        if (cachedXtreamSeries.isNotEmpty()) return cachedXtreamSeries
        return withContext(Dispatchers.IO) {
            val diskCache: XtreamDiskCache<XtreamSeriesItem>? = readDiskCache(seriesDiskCacheFile(creds), seriesDiskCacheType)
            if (diskCache != null && diskCache.items.isNotEmpty()) {
                cachedXtreamSeries = diskCache.items
                xtreamSeriesLoadedAtMs = diskCache.savedAtMs
                diskCache.items
            } else {
                emptyList()
            }
        }
    }

    private suspend fun loadXtreamSeriesEpisodes(
        creds: XtreamCredentials,
        seriesId: Int,
        fast: Boolean = false
    ): List<XtreamSeriesEpisode> {
        ensureXtreamVodCacheOwnership(creds)
        val cached = cachedXtreamSeriesEpisodes[seriesId]
        if (!cached.isNullOrEmpty()) return cached

        val existingInFlight = xtreamSeriesEpisodeInFlightMutex.withLock {
            xtreamSeriesEpisodeInFlight[seriesId]
        }
        if (existingInFlight != null) return existingInFlight.await()

        return coroutineScope {
            val created = async(Dispatchers.IO) {
                val url = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_series_info&series_id=$seriesId"
                val info: JsonObject = requestJson(
                    url,
                    JsonObject::class.java,
                    client = if (fast) xtreamLookupHttpClient else iptvHttpClient
                ) ?: return@async emptyList()
                val parsed = parseXtreamSeriesEpisodes(info)
                if (!fast && parsed.isNotEmpty()) {
                    xtreamSeriesEpisodeCacheMutex.withLock {
                        val next = LinkedHashMap(cachedXtreamSeriesEpisodes)
                        next[seriesId] = parsed
                        while (next.size > maxSeriesEpisodeCacheEntries) {
                            val oldestKey = next.keys.firstOrNull() ?: break
                            next.remove(oldestKey)
                        }
                        cachedXtreamSeriesEpisodes = next
                    }
                }
                parsed
            }

            val deferred = xtreamSeriesEpisodeInFlightMutex.withLock {
                val race = xtreamSeriesEpisodeInFlight[seriesId]
                if (race != null) {
                    created.cancel()
                    race
                } else {
                    xtreamSeriesEpisodeInFlight = xtreamSeriesEpisodeInFlight + (seriesId to created)
                    created
                }
            }

            try {
                deferred.await()
            } finally {
                xtreamSeriesEpisodeInFlightMutex.withLock {
                    if (xtreamSeriesEpisodeInFlight[seriesId] === deferred) {
                        xtreamSeriesEpisodeInFlight = xtreamSeriesEpisodeInFlight - seriesId
                    }
                }
            }
        }
    }

    private suspend fun getXtreamSeriesEpisodes(
        creds: XtreamCredentials,
        seriesId: Int,
        allowNetwork: Boolean,
        fast: Boolean = false
    ): List<XtreamSeriesEpisode> {
        if (allowNetwork) return loadXtreamSeriesEpisodes(creds, seriesId, fast = fast)
        ensureXtreamVodCacheOwnership(creds)
        return cachedXtreamSeriesEpisodes[seriesId].orEmpty()
    }

    private fun parseXtreamSeriesEpisodes(root: JsonObject): List<XtreamSeriesEpisode> {
        val episodesElement = root.get("episodes") ?: return emptyList()
        val episodeObjects = mutableListOf<Pair<JsonObject, Int?>>()
        collectXtreamEpisodeObjects(episodesElement, seasonHint = null, out = episodeObjects)
        if (episodeObjects.isEmpty()) return emptyList()

        return episodeObjects.mapNotNull { (item, seasonHint) ->
            parseEpisodeObject(item, seasonHint = seasonHint, fallbackIndex = null)
        }
    }

    private fun parseSeasonKey(raw: String): Int? {
        if (raw.isBlank()) return null
        val parsed = raw.toIntOrNull() ?: Regex("""\d{1,2}""").find(raw)?.value?.toIntOrNull()
        return parsed?.takeIf { it in 0..99 }
    }

    private fun parseSeasonEpisodes(seasonKey: Int?, array: JsonArray): List<XtreamSeriesEpisode> {
        val out = mutableListOf<XtreamSeriesEpisode>()
        array.forEachIndexed { index, element ->
            val item = element?.asJsonObject ?: return@forEachIndexed
            parseEpisodeObject(item, seasonHint = seasonKey, fallbackIndex = index)?.let { out += it }
        }
        return out
    }

    private fun collectXtreamEpisodeObjects(
        element: com.google.gson.JsonElement?,
        seasonHint: Int?,
        out: MutableList<Pair<JsonObject, Int?>>
    ) {
        when {
            element == null || element.isJsonNull -> return
            element.isJsonArray -> {
                element.asJsonArray.forEach { child ->
                    collectXtreamEpisodeObjects(child, seasonHint, out)
                }
            }
            !element.isJsonObject -> return
            else -> {
                val obj = element.asJsonObject
                val objectSeasonHint = seasonHint
                    ?: parseFlexibleInt(obj.get("season"))
                    ?: parseFlexibleInt(obj.get("season_number"))
                    ?: parseFlexibleInt(obj.get("season_num"))
                if (looksLikeXtreamEpisodeObject(obj)) {
                    out += obj to objectSeasonHint
                    return
                }

                val nestedEpisodes = obj.get("episodes")
                if (nestedEpisodes != null && !nestedEpisodes.isJsonNull) {
                    collectXtreamEpisodeObjects(nestedEpisodes, objectSeasonHint, out)
                }

                obj.entrySet().forEach { (key, value) ->
                    if (key.equals("episodes", ignoreCase = true)) return@forEach
                    val keyedSeasonHint = parseSeasonKey(key) ?: objectSeasonHint
                    collectXtreamEpisodeObjects(value, keyedSeasonHint, out)
                }
            }
        }
    }

    private fun looksLikeXtreamEpisodeObject(obj: JsonObject): Boolean {
        if (parseFlexibleInt(obj.get("id")) != null) return true
        if (parseFlexibleInt(obj.get("stream_id")) != null) return true
        if (parseFlexibleInt(obj.get("episode_id")) != null) return true
        if (parseFlexibleInt(obj.get("episode_num")) != null) return true
        if (parseFlexibleInt(obj.get("episode_number")) != null) return true
        val infoObj = obj.getAsJsonObject("info")
        if (infoObj != null) {
            if (parseFlexibleInt(infoObj.get("id")) != null) return true
            if (parseFlexibleInt(infoObj.get("stream_id")) != null) return true
            if (parseFlexibleInt(infoObj.get("episode_id")) != null) return true
            if (parseFlexibleInt(infoObj.get("episode_num")) != null) return true
            if (parseFlexibleInt(infoObj.get("episode_number")) != null) return true
        }
        return false
    }

    private fun parseEpisodeObject(
        item: JsonObject,
        seasonHint: Int?,
        fallbackIndex: Int?
    ): XtreamSeriesEpisode? {
        val infoObj = item.getAsJsonObject("info")
        val rawTitle = item.get("title")?.asString?.trim().orEmpty().ifBlank {
            infoObj?.get("title")?.asString?.trim().orEmpty()
        }
        val parsedSeasonEpisode = extractSeasonEpisodeFromName(rawTitle)
        val resolvedSeason = seasonHint
            ?: parseFlexibleInt(item.get("season"))
            ?: parseFlexibleInt(item.get("season_number"))
            ?: parseFlexibleInt(item.get("season_num"))
            ?: parseFlexibleInt(infoObj?.get("season"))
            ?: parseFlexibleInt(infoObj?.get("season_number"))
            ?: parseFlexibleInt(infoObj?.get("season_num"))
            ?: parsedSeasonEpisode?.first
            ?: 1
        val episodeNum = parseFlexibleInt(item.get("episode_num"))
            ?: parseFlexibleInt(item.get("episode"))
            ?: parseFlexibleInt(item.get("episode_number"))
            ?: parseFlexibleInt(item.get("number"))
            ?: parseFlexibleInt(item.get("sort"))
            ?: parseFlexibleInt(item.get("sort_order"))
            ?: parseFlexibleInt(infoObj?.get("episode_num"))
            ?: parseFlexibleInt(infoObj?.get("episode"))
            ?: parseFlexibleInt(infoObj?.get("episode_number"))
            ?: parseFlexibleInt(infoObj?.get("number"))
            ?: parseFlexibleInt(infoObj?.get("sort"))
            ?: parsedSeasonEpisode?.second
            ?: extractEpisodeOnlyFromName(rawTitle)
            ?: fallbackIndex?.let { it + 1 }
            ?: 1
        val id = parseFlexibleInt(item.get("id"))
            ?: parseFlexibleInt(item.get("stream_id"))
            ?: parseFlexibleInt(item.get("episode_id"))
            ?: parseFlexibleInt(infoObj?.get("id"))
            ?: parseFlexibleInt(infoObj?.get("stream_id"))
            ?: parseFlexibleInt(infoObj?.get("episode_id"))
            ?: return null
        val title = rawTitle.ifBlank { "S${resolvedSeason}E${episodeNum}" }
        val ext = item.get("container_extension")?.asString?.trim()?.ifBlank { null }
            ?: infoObj?.get("container_extension")?.asString?.trim()?.ifBlank { null }
        return XtreamSeriesEpisode(
            id = id,
            season = resolvedSeason,
            episode = episodeNum,
            title = title,
            containerExtension = ext
        )
    }

    private fun parseFlexibleInt(element: com.google.gson.JsonElement?): Int? {
        if (element == null || element.isJsonNull) return null
        return runCatching {
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> {
                    val number = element.asDouble
                    if (number.isFinite()) number.toInt() else null
                }
                element.isJsonPrimitive -> {
                    val raw = element.asString.trim()
                    raw.toIntOrNull()
                        ?: raw.toDoubleOrNull()?.toInt()
                        ?: Regex("""\d{1,4}""").find(raw)?.value?.toIntOrNull()
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun normalizeLookupText(value: String): String {
        if (value.isBlank()) return ""
        return value
            .replace(bracketContentRegex, " ")
            .replace(parenContentRegex, " ")
            .replace(yearParenRegex, " ")
            .replace(seasonTokenRegex, " ")
            .replace(episodeTokenRegex, " ")
            .replace(releaseTagRegex, " ")
            .lowercase(Locale.US)
            .replace(nonAlphaNumRegex, " ")
            .trim()
            .replace(multiSpaceRegex, " ")
    }

    private val titleTokenNoise = setOf(
        "the", "a", "an", "and", "of", "to", "in", "on",
        "complete", "series", "tv", "show", "season", "seasons",
        "episode", "episodes", "part", "collection", "pack"
    )

    private fun extractTitleTokens(value: String): Set<String> {
        val normalized = normalizeLookupText(value)
        return extractTitleTokensFromNormalized(normalized)
    }

    /** Fast version: skips redundant normalizeLookupText when input is already normalized. */
    private fun extractTitleTokensFromNormalized(normalized: String): Set<String> {
        if (normalized.isBlank()) return emptySet()
        return normalized
            .split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 && it !in titleTokenNoise }
            .toSet()
    }

    private fun toCanonicalTitleKey(value: String): String {
        val tokens = extractTitleTokens(value)
        if (tokens.isEmpty()) return ""
        return tokens.sorted().joinToString(" ")
    }

    /** Fast version: uses pre-computed tokens. */
    private fun toCanonicalTitleKeyFromTokens(tokens: Set<String>): String {
        if (tokens.isEmpty()) return ""
        return tokens.sorted().joinToString(" ")
    }

    private fun normalizeImdbId(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.trim().lowercase(Locale.US)
        val match = Regex("tt\\d{5,10}").find(cleaned)?.value
        return match ?: cleaned.takeIf { it.startsWith("tt") && it.length >= 7 }
    }

    private fun normalizeTmdbId(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val digits = Regex("\\d{1,10}").find(value.trim())?.value
        return digits?.trimStart('0')?.ifBlank { "0" }
    }

    private fun normalizeTmdbId(value: Int?): String? {
        if (value == null || value <= 0) return null
        return value.toString()
    }

    private fun parseYear(value: String): Int? {
        return Regex("(19|20)\\d{2}")
            .find(value)
            ?.value
            ?.toIntOrNull()
    }

    private fun extractSeasonEpisodeFromName(value: String): Pair<Int, Int>? {
        val normalized = value.lowercase(Locale.US)
        val patterns = listOf(
            Regex("""\bs(\d{1,2})\s*[\.\-_ ]*\s*e(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{1,2})x(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\bseason\s*(\d{1,2}).*episode\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\bseason\s*(\d{1,2}).*ep(?:isode)?\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{1,2})\.(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d)(\d{2})\b""", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { regex ->
            val match = regex.find(normalized) ?: return@forEach
            val season = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val episode = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            return Pair(season, episode)
        }
        return null
    }

    private fun extractEpisodeOnlyFromName(value: String): Int? {
        val normalized = value.lowercase(Locale.US)
        val patterns = listOf(
            Regex("""\bepisode\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\bep\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\be(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""\bpart\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE),
            Regex("""[\[\(\- ](\d{1,3})[\]\) ]?$""", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { regex ->
            val match = regex.find(normalized) ?: return@forEach
            val episode = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            if (episode > 0) return episode
        }
        return null
    }

    private fun pickEpisodeMatch(
        episodes: List<XtreamSeriesEpisode>,
        season: Int,
        episode: Int
    ): XtreamSeriesEpisode? {
        if (episodes.isEmpty()) return null
        episodes.firstOrNull { it.season == season && it.episode == episode }?.let { return it }

        // Some providers flatten seasoning; if exactly one candidate has the episode number, use it.
        val byEpisode = episodes.filter { it.episode == episode }
        if (byEpisode.size == 1) return byEpisode.first()
        if (byEpisode.isNotEmpty()) {
            return byEpisode.minByOrNull { kotlin.math.abs(it.season - season) }
        }
        return null
    }

    private fun scoreNameMatch(providerName: String, normalizedInput: String): Int {
        val normalizedProvider = normalizeLookupText(providerName)
        if (normalizedProvider.isBlank() || normalizedInput.isBlank()) return 0
        if (normalizedProvider == normalizedInput) return 120
        if (normalizedProvider.contains(normalizedInput)) return 90
        if (normalizedInput.contains(normalizedProvider)) return 70
        if (normalizedProvider.startsWith(normalizedInput) || normalizedInput.startsWith(normalizedProvider)) return 68
        val stopWords = setOf("the", "a", "an", "and", "of", "part", "episode", "season", "movie")
        val providerWords = normalizedProvider
            .split(' ')
            .filter { it.isNotBlank() && it !in stopWords }
            .toSet()
        val inputWords = normalizedInput
            .split(' ')
            .filter { it.isNotBlank() && it !in stopWords }
            .toSet()
        if (providerWords.isEmpty() || inputWords.isEmpty()) return 0
        val overlap = providerWords.intersect(inputWords).size
        val coverage = overlap.toDouble() / inputWords.size.toDouble()
        return when {
            overlap >= 2 && coverage >= 0.75 -> 75 + overlap
            overlap >= 2 -> 55 + overlap
            overlap == 1 && inputWords.size >= 3 && providerWords.size >= 3 -> 42
            overlap == 1 && inputWords.size <= 2 -> 35
            else -> 0
        }
    }

    private fun looseSeriesTitleScore(providerName: String, normalizedInput: String): Int {
        val normalizedProvider = normalizeLookupText(providerName)
        if (normalizedProvider.isBlank() || normalizedInput.isBlank()) return 0
        val providerWords = normalizedProvider.split(' ').filter { it.length >= 3 && it !in titleTokenNoise }.toSet()
        val inputWords = normalizedInput.split(' ').filter { it.length >= 3 && it !in titleTokenNoise }.toSet()
        if (providerWords.isEmpty() || inputWords.isEmpty()) return 0
        val overlap = providerWords.intersect(inputWords).size
        return when {
            overlap >= 2 -> 50 + overlap
            overlap == 1 -> 24
            else -> 0
        }
    }

    private fun rankSeriesCandidate(
        providerName: String,
        normalizedInput: String,
        baseScore: Int
    ): Int {
        val normalizedProvider = normalizeLookupText(providerName)
        if (normalizedProvider.isBlank() || normalizedInput.isBlank()) return baseScore
        var score = baseScore
        if (normalizedProvider == normalizedInput) score += 500
        if (normalizedProvider.contains(normalizedInput)) score += 320
        if (normalizedInput.contains(normalizedProvider)) score += 180
        val providerHead = normalizedProvider.split(' ').take(2).joinToString(" ")
        if (providerHead.isNotBlank() && normalizedInput.startsWith(providerHead)) score += 110
        return score
    }

    private fun inferQuality(value: String): String {
        val lower = value.lowercase(Locale.US)
        return when {
            lower.contains("2160") || lower.contains("4k") -> "4K"
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("480") -> "480p"
            else -> "VOD"
        }
    }

    private fun resolveXtreamCredentials(url: String): XtreamCredentials? {
        if (url.isBlank()) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        val username = parsed.queryParameter("username")?.trim()?.ifBlank { null }
            ?: parsed.queryParameter("user")?.trim()?.ifBlank { null }
            ?: parsed.queryParameter("uname")?.trim()?.ifBlank { null }
            ?: ""
        val password = parsed.queryParameter("password")?.trim()?.ifBlank { null }
            ?: parsed.queryParameter("pass")?.trim()?.ifBlank { null }
            ?: parsed.queryParameter("pwd")?.trim()?.ifBlank { null }
            ?: ""
        if (username.isBlank() || password.isBlank()) return null
        // Accept any URL with username/password params; derive baseUrl from scheme+host+port
        val path = parsed.encodedPath.lowercase(Locale.US)
        val knownXtreamPath = path.endsWith("/get.php") || path.endsWith("/xmltv.php") || path.endsWith("/player_api.php")
        val baseUrl = if (knownXtreamPath) {
            parsed.toXtreamBaseUrl()
        } else {
            // Derive from scheme + host + port for non-standard paths
            buildString {
                append(parsed.scheme)
                append("://")
                append(parsed.host)
                if (parsed.port != if (parsed.scheme == "https") 443 else 80) {
                    append(":${parsed.port}")
                }
            }
        }
        return XtreamCredentials(baseUrl, username, password)
    }

    /**
     * Build the complete M3U URL to use for fetching, with embedded credentials if stored separately.
     * This ensures credentials stored separately are properly embedded in the fetching URLs.
     */
    private fun buildFetchM3uUrl(config: IptvConfig): String {
        // If credentials are stored separately, build a complete M3U URL with embedded credentials
        if (config.xtreamUsername.isNotBlank() && config.xtreamPassword.isNotBlank()) {
            // config.m3uUrl should be just the host in this case
            val host = config.m3uUrl.trim().removeSuffix("/")
            if (host.isNotBlank()) {
                // Ensure the host has the http:// or https:// prefix if not present
                val fullHost = when {
                    host.contains("://") -> host
                    else -> "http://$host"
                }
                // Build the Xtream M3U URL with embedded credentials
                return "$fullHost/get.php?username=${config.xtreamUsername}&password=${config.xtreamPassword}&type=m3u_plus&output=ts"
            }
        }
        // Otherwise use as-is (might already have embedded credentials or be a plain M3U URL)
        return config.m3uUrl
    }

    /**
     * Resolve Xtream credentials from config, checking both stored credentials and URL-embedded ones.
     * Stored credentials take priority over URL-embedded credentials.
     */
    private fun resolveXtreamCredentialsFromConfig(config: IptvConfig, urlField: String): XtreamCredentials? {
        // Check stored credentials first (highest priority)
        if (config.xtreamUsername.isNotBlank() && config.xtreamPassword.isNotBlank()) {
            // Derive base URL from the host stored in the config
            val host = when {
                urlField.contains("://") -> urlField.substringAfter("://").substringBefore("/")
                else -> urlField
            }
            val baseUrl = if (host.isNotBlank()) {
                when {
                    host.startsWith("http", ignoreCase = true) -> host
                    else -> "http://$host"
                }
            } else {
                return null
            }
            return XtreamCredentials(baseUrl, config.xtreamUsername, config.xtreamPassword)
        }
        // Fall back to credentials embedded in URL
        return resolveXtreamCredentials(urlField)
    }

    private fun resolveEpgCandidates(config: IptvConfig): List<String> {
        val manual = config.epgUrl.takeIf { it.isNotBlank() }
        val creds = resolveXtreamCredentialsFromConfig(config, config.epgUrl).let { fromEpg ->
            fromEpg ?: resolveXtreamCredentialsFromConfig(config, config.m3uUrl)
        }
        val derived = if (creds != null) {
            buildList {
            preferredDerivedEpgUrl?.takeIf { it.startsWith(creds.baseUrl) }?.let { add(it) }
            add("${creds.baseUrl}/xmltv.php?username=${creds.username}&password=${creds.password}")
            add("${creds.baseUrl}/get.php?username=${creds.username}&password=${creds.password}&type=xmltv")
            add("${creds.baseUrl}/get.php?username=${creds.username}&password=${creds.password}&type=xml")
            add("${creds.baseUrl}/xmltv.php")
            add("${creds.baseUrl}/get.php?username=${creds.username}&password=${creds.password}")
        }
        } else {
            emptyList()
        }

        return buildList {
            manual?.let { add(it) }
            discoveredM3uEpgUrl?.let { add(it) }
            addAll(derived)
        }.distinct()
    }

    private fun fetchXtreamLiveChannels(
        creds: XtreamCredentials,
        onProgress: (IptvLoadProgress) -> Unit
    ): List<IptvChannel> {
        val categoriesUrl = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_live_categories"
        val streamsUrl = "${creds.baseUrl}/player_api.php?username=${creds.username}&password=${creds.password}&action=get_live_streams"

        onProgress(IptvLoadProgress("Loading categories...", 10))
        val categories: List<XtreamLiveCategory> =
            requestJson(categoriesUrl, object : TypeToken<List<XtreamLiveCategory>>() {}.type) ?: emptyList()
        val categoryMap = categories
            .associate { it.categoryId.orEmpty() to (it.categoryName?.trim().orEmpty().ifBlank { "Uncategorized" }) }

        onProgress(IptvLoadProgress("Loading live streams...", 35))
        val streams: List<XtreamLiveStream> =
            requestJson(streamsUrl, object : TypeToken<List<XtreamLiveStream>>() {}.type) ?: emptyList()
        if (streams.isEmpty()) return emptyList()

        val total = streams.size.coerceAtLeast(1)
        return streams.mapIndexedNotNull { index, stream ->
            if (index % 500 == 0) {
                val pct = (35 + ((index.toLong() * 55L) / total.toLong())).toInt().coerceIn(35, 90)
                onProgress(IptvLoadProgress("Parsing provider streams... $index/$total", pct))
            }

            val streamId = stream.streamId ?: return@mapIndexedNotNull null
            val name = stream.name
                ?.trim()
                .orEmpty()
                .ifBlank { return@mapIndexedNotNull null }
                .let { raw ->
                    if (":" in raw) {
                        val potentialLanguage = raw.substringBefore(":").trim()
                        val channel = raw.substringAfter(":").trim()
                        "$channel ($potentialLanguage)"
                    } else {
                        raw
                    }
                }
            val group = categoryMap[stream.categoryId.orEmpty()].orEmpty().ifBlank { "Uncategorized" }
            val streamUrl = "${creds.baseUrl}/${creds.username}/${creds.password}/$streamId"

            IptvChannel(
                id = "xtream:$streamId",
                name = name,
                streamUrl = streamUrl,
                group = group,
                logo = stream.streamIcon?.takeIf { it.isNotBlank() },
                epgId = stream.epgChannelId?.trim()?.takeIf { it.isNotBlank() },
                rawTitle = name,
                xtreamStreamId = streamId
            )
        }
    }

    private fun <T> requestJson(
        url: String,
        type: Type,
        client: OkHttpClient = iptvHttpClient
    ): T? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.CUSTOM_AGENT)
            .header("Accept", "application/json,*/*")
            .get()
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            if (body.isBlank()) return null
            return runCatching { gson.fromJson<T>(body, type) }.getOrNull()
        }
    }

    private fun fetchAndParseM3uOnce(
        url: String,
        onProgress: (IptvLoadProgress) -> Unit
    ): List<IptvChannel> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.CUSTOM_AGENT)
            .header("Accept", "*/*")
            .get()
            .build()
        iptvHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.byteStream() ?: throw IllegalStateException("M3U response was empty.")
            val contentLength = response.body?.contentLength()?.takeIf { it > 0L }
            val progressStream = ProgressInputStream(raw) { bytesRead ->
                if (contentLength != null) {
                    val pct = ((bytesRead * 70L) / contentLength).toInt().coerceIn(8, 74)
                    onProgress(IptvLoadProgress("Downloading playlist... $pct%", pct))
                } else {
                    onProgress(IptvLoadProgress("Downloading playlist...", 15))
                }
            }
            val stream = BufferedInputStream(progressStream)
            if (!response.isSuccessful && !looksLikeM3u(stream)) {
                val preview = response.peekBody(220).string().replace('\n', ' ').trim()
                val detail = if (preview.isBlank()) "No response body." else preview
                throw IllegalStateException("M3U request failed (HTTP ${response.code}). $detail")
            }
            onProgress(IptvLoadProgress("Parsing channels...", 78))
            return parseM3u(stream, onProgress)
        }
    }

    private fun fetchAndParseEpg(url: String, channels: List<IptvChannel>): Map<String, IptvNowNext> {
        fun epgRequest(targetUrl: String, userAgent: String): Request {
            return Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
        }

        var response = iptvHttpClient.newCall(epgRequest(url, "VLC/3.0.20 LibVLC/3.0.20")).execute()
        if (!response.isSuccessful && response.code in setOf(511, 403, 401)) {
            response.close()
            response = iptvHttpClient.newCall(
                epgRequest(
                    url,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
            ).execute()
        }
        response.use { safeResponse ->
            val stream = safeResponse.body?.byteStream() ?: throw IllegalStateException("Empty EPG response")
            val prepared = BufferedInputStream(prepareInputStream(stream, url))
            if (!safeResponse.isSuccessful && !looksLikeXmlTv(prepared)) {
                val preview = safeResponse.peekBody(220).string().replace('\n', ' ').trim()
                val detail = if (preview.isBlank()) "No response body." else preview
                throw IllegalStateException("EPG request failed (HTTP ${safeResponse.code}). $detail")
            }

            // Try streaming parse first (avoids disk I/O for the common case).
            // Only spool to disk and retry if the stream parse fails.
            try {
                val sanitized = BackslashEscapeSanitizingInputStream(prepared)
                return parseXmlTvNowNext(BufferedInputStream(sanitized), channels)
            } catch (streamError: Exception) {
                // Streaming parse failed – the network stream is consumed, so we
                // cannot retry from it.  Check if we got a useful partial result
                // or need to re-download.  Re-download and spool to disk for retries.
                val tmpFile = File.createTempFile("epg_", ".xml", context.cacheDir)
                try {
                    // Re-download
                    val retryResponse = iptvHttpClient.newCall(
                        epgRequest(url, "VLC/3.0.20 LibVLC/3.0.20")
                    ).execute()
                    retryResponse.use { rr ->
                        val retryStream = rr.body?.byteStream()
                            ?: throw IllegalStateException("Empty EPG retry response")
                        BufferedInputStream(prepareInputStream(retryStream, url)).use { input ->
                            BufferedOutputStream(tmpFile.outputStream()).use { output ->
                                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                            }
                        }
                    }

                    try {
                        return FileInputStream(tmpFile).use { input ->
                            parseXmlTvNowNext(BufferedInputStream(input), channels)
                        }
                    } catch (_: Exception) {
                        // Final fallback: SAX parser (different engine).
                        return FileInputStream(tmpFile).use { input ->
                            val sanitized2 = BackslashEscapeSanitizingInputStream(BufferedInputStream(input))
                            parseXmlTvNowNextWithSax(BufferedInputStream(sanitized2), channels)
                        }
                    }
                } finally {
                    tmpFile.delete()
                    cleanupStaleEpgTempFiles(maxAgeMs = 60_000L)
                }
            }
        }
    }

    // ── Xtream Short EPG (instant loading) ─────────────────────────────

    /**
     * Data class for a single EPG listing returned by the Xtream short EPG APIs.
     * Fields like `title` and `description` are base64-encoded by the server.
     */
    private data class XtreamEpgListing(
        val id: String? = null,
        @SerializedName("epg_id") val epgId: String? = null,
        val title: String? = null,
        val lang: String? = null,
        val start: String? = null,
        val end: String? = null,
        val description: String? = null,
        @SerializedName("channel_id") val channelId: String? = null,
        @SerializedName("start_timestamp") val startTimestamp: String? = null,
        @SerializedName("stop_timestamp") val stopTimestamp: String? = null,
        @SerializedName("stream_id") val streamId: String? = null
    )

    private data class XtreamEpgResponse(
        @SerializedName("epg_listings") val epgListings: List<XtreamEpgListing>? = null
    )

    /**
     * Decode a base64-encoded string from the Xtream short EPG API.
     * Returns the decoded text or the original string if decoding fails.
     */
    private fun decodeBase64Field(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return try {
            String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8).trim()
        } catch (_: Exception) {
            encoded.trim()  // Not base64; return raw
        }
    }

    /**
     * Fetch EPG data using the Xtream Codes `get_simple_data_table` API.
     * This returns current/next program data for ALL channels in one lightweight
     * JSON response, instead of downloading a 20-150+ MB XMLTV XML file.
     *
     * Returns null if the API is not supported or fails (caller should fall back to XMLTV).
     */
    /**
     * Extract the Xtream stream ID from an IptvChannel.
     * Uses the explicit field if set, otherwise parses from the "xtream:123" id format.
     */
    private fun resolveXtreamStreamId(ch: IptvChannel): Int? {
        ch.xtreamStreamId?.let { return it }
        if (ch.id.startsWith("xtream:")) {
            return ch.id.removePrefix("xtream:").toIntOrNull()
        }
        return null
    }

    private fun fetchXtreamShortEpg(
        creds: XtreamCredentials,
        channels: List<IptvChannel>,
        onProgress: (IptvLoadProgress) -> Unit
    ): Map<String, IptvNowNext>? {
        if (channels.isEmpty()) return null

        // Build lookups: epgId -> channelIds, streamId -> channelIds
        val epgIdToChannelIds = mutableMapOf<String, MutableList<String>>()
        val streamIdToChannelIds = mutableMapOf<String, MutableList<String>>()
        for (ch in channels) {
            ch.epgId?.let { eid ->
                epgIdToChannelIds.getOrPut(eid) { mutableListOf() }.add(ch.id)
            }
            resolveXtreamStreamId(ch)?.let { sid ->
                streamIdToChannelIds.getOrPut(sid.toString()) { mutableListOf() }.add(ch.id)
            }
        }

        onProgress(IptvLoadProgress("Loading EPG (fast Xtream API)...", 90))

        // Prioritize: favorite channels first, then favorite groups, then rest.
        // Deduplicate stream IDs so we don't fetch the same channel twice.
        val xtreamChannels = channels.filter { resolveXtreamStreamId(it) != null }
        val favoriteChannelIds = runCatching { kotlinx.coroutines.runBlocking { observeFavoriteChannels().first() } }
            .getOrDefault(emptyList()).toSet()
        val favoriteGroupNames = runCatching { kotlinx.coroutines.runBlocking { observeFavoriteGroups().first() } }
            .getOrDefault(emptyList()).toSet()

        val favChannels = xtreamChannels.filter { it.id in favoriteChannelIds }
        val favGroupChannels = xtreamChannels.filter { it.id !in favoriteChannelIds && it.group in favoriteGroupNames }
        val alreadyPrioritized = (favChannels.map { it.id } + favGroupChannels.map { it.id }).toSet()
        val rest = xtreamChannels.filter { it.id !in alreadyPrioritized }
        val prioritized = favChannels + favGroupChannels + rest

        val toFetch = prioritized.take(2000)
        System.err.println("[EPG] Xtream short EPG: fetching ${toFetch.size}/${xtreamChannels.size} channels")
        if (toFetch.isEmpty()) return null

        // Parallel fetch using a thread pool (20 concurrent connections)
        val allListings = java.util.Collections.synchronizedList(mutableListOf<XtreamEpgListing>())
        val errorCount = java.util.concurrent.atomic.AtomicInteger(0)
        val fetchedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val total = toFetch.size
        val executor = java.util.concurrent.Executors.newFixedThreadPool(20)
        val futures = mutableListOf<java.util.concurrent.Future<*>>()

        val sampleLogged = java.util.concurrent.atomic.AtomicBoolean(false)
        for (ch in toFetch) {
            val sid = resolveXtreamStreamId(ch) ?: continue
            futures.add(executor.submit {
                val url = "${creds.baseUrl}/player_api.php?username=${creds.username}" +
                    "&password=${creds.password}&action=get_short_epg&stream_id=$sid&limit=5"
                try {
                    val resp: XtreamEpgResponse? = requestJson(url, XtreamEpgResponse::class.java)
                    val listings = resp?.epgListings
                    if (listings != null) {
                        allListings.addAll(listings)
                        // Log first successful response for debugging
                        if (listings.isNotEmpty() && sampleLogged.compareAndSet(false, true)) {
                            val sample = listings.first()
                            System.err.println("[EPG] Sample response for stream_id=$sid: channelId=${sample.channelId} epgId=${sample.epgId} streamId=${sample.streamId} start=${sample.start} startTs=${sample.startTimestamp} title=${sample.title?.take(40)}")
                        }
                    }
                } catch (_: Exception) { errorCount.incrementAndGet() }
                val done = fetchedCount.incrementAndGet()
                if (done % 50 == 0) {
                    val pct = (90 + ((done.toLong() * 8L) / total.toLong())).toInt().coerceIn(90, 98)
                    onProgress(IptvLoadProgress("Loading EPG... $done/$total channels", pct))
                }
            })
        }

        // Wait for all to complete (with timeout)
        try {
            executor.shutdown()
            executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            executor.shutdownNow()
        }

        val errors = errorCount.get()
        val fetched = fetchedCount.get()
        System.err.println("[EPG] Xtream short EPG done: ${allListings.size} listings, $fetched fetched, $errors errors")

        if (errors > fetched / 2 && fetched > 20) {
            return null
        }
        if (allListings.isEmpty()) return null

        onProgress(IptvLoadProgress("Parsing EPG data (${allListings.size} listings)...", 98))
        return buildNowNextFromXtreamListings(allListings, epgIdToChannelIds, streamIdToChannelIds)
    }



    /**
     * Build IptvNowNext map from Xtream EPG listings.
     * Groups listings by channel, sorts by start time, assigns now/next/later/upcoming.
     */
    private fun buildNowNextFromXtreamListings(
        listings: List<XtreamEpgListing>,
        epgIdToChannelIds: Map<String, List<String>>,
        streamIdToChannelIds: Map<String, List<String>>
    ): Map<String, IptvNowNext> {
        val nowMs = System.currentTimeMillis()
        val recentCutoff = nowMs - (15L * 60_000L) // 15 min ago

        // Group listings by channel.
        // Try matching by: epg_id (channelId field), then stream_id.
        data class ChannelPrograms(val programs: MutableList<IptvProgram> = mutableListOf())
        val channelProgramsMap = mutableMapOf<String, ChannelPrograms>()

        for (listing in listings) {
            val startMs = listing.startTimestamp?.toLongOrNull()?.let { it * 1000L }
                ?: parseXtreamDateTime(listing.start)
                ?: continue
            val stopMs = listing.stopTimestamp?.toLongOrNull()?.let { it * 1000L }
                ?: parseXtreamDateTime(listing.end)
                ?: continue

            // Skip programs that ended well before now (keep recent ones)
            if (stopMs < recentCutoff) continue

            val title = decodeBase64Field(listing.title).ifBlank { "No Title" }
            val description = decodeBase64Field(listing.description).takeIf { it.isNotBlank() }

            val program = IptvProgram(
                title = title,
                description = description,
                startUtcMillis = startMs,
                endUtcMillis = stopMs
            )

            // Resolve which IptvChannel IDs this listing maps to
            val resolvedChannelIds = mutableSetOf<String>()

            // Match by epg_id / channel_id field
            listing.channelId?.let { cid ->
                epgIdToChannelIds[cid]?.let { resolvedChannelIds.addAll(it) }
            }
            listing.epgId?.let { eid ->
                // epg_id can be the stream_id in some providers
                streamIdToChannelIds[eid]?.let { resolvedChannelIds.addAll(it) }
                epgIdToChannelIds[eid]?.let { resolvedChannelIds.addAll(it) }
            }
            // Match by stream_id
            listing.streamId?.let { sid ->
                streamIdToChannelIds[sid]?.let { resolvedChannelIds.addAll(it) }
            }

            for (channelId in resolvedChannelIds) {
                channelProgramsMap.getOrPut(channelId) { ChannelPrograms() }.programs.add(program)
            }
        }

        System.err.println("[EPG] buildNowNext: ${listings.size} listings -> ${channelProgramsMap.size} channels mapped (epgIdMap=${epgIdToChannelIds.size}, streamIdMap=${streamIdToChannelIds.size})")
        if (channelProgramsMap.isEmpty() && listings.isNotEmpty()) {
            // Log first few listings to debug mapping issues
            listings.take(3).forEach { l ->
                System.err.println("[EPG]   sample listing: channelId=${l.channelId} epgId=${l.epgId} streamId=${l.streamId} start=${l.start} startTs=${l.startTimestamp} title=${l.title?.take(30)}")
            }
        }

        // Build NowNext from sorted programs
        val result = mutableMapOf<String, IptvNowNext>()
        for ((channelId, cp) in channelProgramsMap) {
            val sorted = cp.programs.sortedBy { it.startUtcMillis }
            var now: IptvProgram? = null
            var next: IptvProgram? = null
            var later: IptvProgram? = null
            val upcoming = mutableListOf<IptvProgram>()
            val recent = mutableListOf<IptvProgram>()

            for (p in sorted) {
                when {
                    p.endUtcMillis <= nowMs && p.endUtcMillis > recentCutoff -> recent.add(p)
                    p.isLive(nowMs) -> now = p
                    p.startUtcMillis > nowMs && next == null -> next = p
                    p.startUtcMillis > nowMs && later == null -> later = p
                    p.startUtcMillis > nowMs -> upcoming.add(p)
                }
            }

            result[channelId] = IptvNowNext(
                now = now,
                next = next,
                later = later,
                upcoming = upcoming.take(5),
                recent = recent
            )
        }

        val withNow = result.values.count { it.now != null }
        val withNext = result.values.count { it.next != null }
        System.err.println("[EPG] buildNowNext result: ${result.size} channels, $withNow with NOW, $withNext with NEXT")
        return result
    }

    /**
     * Parse Xtream datetime strings like "2024-01-01 12:00:00" to epoch millis.
     */
    private fun parseXtreamDateTime(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val local = java.time.LocalDateTime.parse(dateStr, formatter)
            // Xtream times are typically UTC
            local.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Some providers return malformed XML text that includes JSON-style backslash escapes
     * (for example: \" or \n) inside element values. KXmlParser can fail hard on this.
     * This filter normalizes the most common escapes into plain text so XML parsing can continue.
     */
    private class BackslashEscapeSanitizingInputStream(
        input: InputStream
    ) : FilterInputStream(input) {
        override fun read(): Int {
            val current = super.read()
            if (current == -1) return -1

            val mapped = if (current == '\\'.code) {
                val next = super.read()
                if (next == -1) {
                    current
                } else {
                    when (next.toChar()) {
                        '\\' -> '\\'.code
                        '"' -> '"'.code
                        '\'' -> '\''.code
                        '/' -> '/'.code
                        'n' -> '\n'.code
                        'r' -> '\r'.code
                        't' -> '\t'.code
                        'b' -> '\b'.code
                        'f' -> 0x0C
                        else -> {
                            // Unknown escape (for example \y): drop the slash and keep the char.
                            next
                        }
                    }
                }
            } else {
                current
            }

            // XML 1.0 forbids most control chars; normalize them to space.
            if (mapped in 0x00..0x1F && mapped != '\n'.code && mapped != '\r'.code && mapped != '\t'.code) {
                return ' '.code
            }
            return mapped
        }
    }

    private fun parseM3u(
        input: InputStream,
        onProgress: (IptvLoadProgress) -> Unit
    ): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var pendingMetadata: String? = null
        var parsedCount = 0

        input.bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach

                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    // Extract EPG URL from M3U header: #EXTM3U url-tvg="..." or x-tvg-url="..."
                    val tvgUrl = extractAttr(line, "url-tvg")
                        ?: extractAttr(line, "x-tvg-url")
                    if (!tvgUrl.isNullOrBlank() && tvgUrl.startsWith("http", ignoreCase = true)) {
                        discoveredM3uEpgUrl = tvgUrl
                    }
                    return@forEach
                }

                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    pendingMetadata = line
                    return@forEach
                }

                if (line.startsWith("#")) return@forEach

                val metadata = pendingMetadata
                pendingMetadata = null

                val channelName = extractChannelName(metadata)
                val groupTitle = extractAttr(metadata, "group-title")?.takeIf { it.isNotBlank() } ?: "Uncategorized"
                val logo = extractAttr(metadata, "tvg-logo")
                val epgId = extractAttr(metadata, "tvg-id")
                val id = buildChannelId(line, epgId)

                channels += IptvChannel(
                    id = id,
                    name = channelName,
                    streamUrl = line,
                    group = groupTitle,
                    logo = logo,
                    epgId = epgId,
                    rawTitle = metadata ?: channelName
                )
                parsedCount++
                if (parsedCount % 10000 == 0) {
                    onProgress(IptvLoadProgress("Parsing channels... $parsedCount found", 85))
                }
            }
        }

        onProgress(IptvLoadProgress("Finalizing ${channels.size} channels...", 95))
        return channels.distinctBy { it.id }
    }

    private fun parseXmlTvNowNext(
        input: InputStream,
        channels: List<IptvChannel>
    ): Map<String, IptvNowNext> {
        if (channels.isEmpty()) return emptyMap()

        val nowUtc = System.currentTimeMillis()
        val recentCutoff = nowUtc - (15 * 60_000L)  // Keep programs that ended within past 15 min
        val keyLookup = buildChannelKeyLookup(channels)
        val xmlChannelNameMap = mutableMapOf<String, MutableSet<String>>()
        val nowCandidates = mutableMapOf<String, IptvProgram?>()
        val upcomingCandidates = mutableMapOf<String, MutableList<IptvProgram>>()
        val recentCandidates = mutableMapOf<String, MutableList<IptvProgram>>()

        var currentXmlChannelId: String? = null
        var currentChannelKey: String? = null
        var currentStart = 0L
        var currentStop = 0L
        var currentTitle: String? = null
        var currentDesc: String? = null

        val parser = android.util.Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.US)) {
                        "channel" -> {
                            currentXmlChannelId = normalizeChannelKey(parser.getAttributeValue(null, "id") ?: "")
                        }
                        "display-name" -> {
                            val xmlId = currentXmlChannelId
                            if (!xmlId.isNullOrBlank()) {
                                val display = normalizeChannelKey(parser.nextText().orEmpty())
                                if (display.isNotBlank()) {
                                    xmlChannelNameMap.getOrPut(xmlId) { mutableSetOf() }.add(display)
                                }
                            }
                        }
                        "programme" -> {
                            val rawKey = normalizeChannelKey(parser.getAttributeValue(null, "channel") ?: "")
                            val stop = parseXmlTvDate(parser.getAttributeValue(null, "stop"))
                            // Skip programmes that ended before the recent cutoff
                            if (stop > 0L && stop <= recentCutoff) {
                                currentChannelKey = null
                            } else {
                                currentChannelKey = rawKey
                                currentStart = parseXmlTvDate(parser.getAttributeValue(null, "start"))
                                currentStop = stop
                                currentTitle = null
                                currentDesc = null
                            }
                        }
                        "title" -> {
                            if (currentChannelKey != null) {
                                currentTitle = parser.nextText().trim().ifBlank { null }
                            }
                        }
                        "desc" -> {
                            if (currentChannelKey != null) {
                                currentDesc = parser.nextText().trim().ifBlank { null }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        parser.name.equals("channel", ignoreCase = true) -> {
                            currentXmlChannelId = null
                        }
                        parser.name.equals("programme", ignoreCase = true) -> {
                        val key = currentChannelKey
                        val channel = key?.let { resolveXmlTvChannel(it, xmlChannelNameMap, keyLookup) }
                        if (channel != null && currentStop > currentStart) {
                            val program = IptvProgram(
                                title = currentTitle ?: "Unknown program",
                                description = currentDesc,
                                startUtcMillis = currentStart,
                                endUtcMillis = currentStop
                            )

                            val nowProgram = pickNow(nowCandidates[channel.id], program, nowUtc)
                            nowCandidates[channel.id] = nowProgram
                            if (program.startUtcMillis > nowUtc) {
                                val future = upcomingCandidates.getOrPut(channel.id) { mutableListOf() }
                                addUpcomingCandidate(future, program, limit = 8)
                            } else if (program.endUtcMillis <= nowUtc && program.endUtcMillis > recentCutoff) {
                                val recent = recentCandidates.getOrPut(channel.id) { mutableListOf() }
                                if (recent.size < 6) recent.add(program)
                            }
                        }
                        currentChannelKey = null
                    }
                    }
                }
            }
            eventType = parser.next()
        }

        return channels.associate { channel ->
            val future = upcomingCandidates[channel.id].orEmpty()
            val recent = recentCandidates[channel.id].orEmpty().sortedBy { it.startUtcMillis }
            channel.id to IptvNowNext(
                now = nowCandidates[channel.id],
                next = future.getOrNull(0),
                later = future.getOrNull(1),
                upcoming = future,
                recent = recent
            )
        }
    }

    private fun parseXmlTvNowNextWithSax(
        input: InputStream,
        channels: List<IptvChannel>
    ): Map<String, IptvNowNext> {
        if (channels.isEmpty()) return emptyMap()

        val keyLookup = buildChannelKeyLookup(channels)
        val xmlChannelNameMap = mutableMapOf<String, MutableSet<String>>()
        val nowCandidates = mutableMapOf<String, IptvProgram?>()
        val upcomingCandidates = mutableMapOf<String, MutableList<IptvProgram>>()
        val recentCandidates = mutableMapOf<String, MutableList<IptvProgram>>()
        val nowUtc = System.currentTimeMillis()
        val recentCutoff = nowUtc - (15 * 60_000L)  // Keep programs that ended within past 15 min

        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://xml.org/sax/features/validation", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val parser = factory.newSAXParser()

        var currentXmlChannelId: String? = null
        var currentChannelKey: String? = null
        var currentStart = 0L
        var currentStop = 0L
        var currentTitle: String? = null
        var currentDesc: String? = null
        var readingDisplayName = false
        var readingTitle = false
        var readingDesc = false
        val textBuffer = StringBuilder(128)

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val name = (localName ?: qName ?: "").lowercase(Locale.US)
                when (name) {
                    "channel" -> {
                        currentXmlChannelId = normalizeChannelKey(attributes?.getValue("id").orEmpty())
                    }
                    "display-name" -> {
                        readingDisplayName = true
                        textBuffer.setLength(0)
                    }
                    "programme" -> {
                        currentChannelKey = normalizeChannelKey(attributes?.getValue("channel").orEmpty())
                        currentStart = parseXmlTvDate(attributes?.getValue("start"))
                        currentStop = parseXmlTvDate(attributes?.getValue("stop"))
                        currentTitle = null
                        currentDesc = null
                    }
                    "title" -> {
                        if (!currentChannelKey.isNullOrBlank()) {
                            readingTitle = true
                            textBuffer.setLength(0)
                        }
                    }
                    "desc" -> {
                        if (!currentChannelKey.isNullOrBlank()) {
                            readingDesc = true
                            textBuffer.setLength(0)
                        }
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (ch == null || length <= 0) return
                if (readingDisplayName || readingTitle || readingDesc) {
                    textBuffer.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val name = (localName ?: qName ?: "").lowercase(Locale.US)
                when (name) {
                    "display-name" -> {
                        if (readingDisplayName) {
                            val xmlId = currentXmlChannelId
                            if (!xmlId.isNullOrBlank()) {
                                val display = normalizeChannelKey(textBuffer.toString())
                                if (display.isNotBlank()) {
                                    xmlChannelNameMap.getOrPut(xmlId) { mutableSetOf() }.add(display)
                                }
                            }
                            readingDisplayName = false
                            textBuffer.setLength(0)
                        }
                    }
                    "channel" -> {
                        currentXmlChannelId = null
                    }
                    "title" -> {
                        if (readingTitle) {
                            currentTitle = textBuffer.toString().trim().ifBlank { null }
                            readingTitle = false
                            textBuffer.setLength(0)
                        }
                    }
                    "desc" -> {
                        if (readingDesc) {
                            currentDesc = textBuffer.toString().trim().ifBlank { null }
                            readingDesc = false
                            textBuffer.setLength(0)
                        }
                    }
                    "programme" -> {
                        val key = currentChannelKey
                        val channel = key?.let { resolveXmlTvChannel(it, xmlChannelNameMap, keyLookup) }
                        if (channel != null && currentStop > currentStart) {
                            val program = IptvProgram(
                                title = currentTitle ?: "Unknown program",
                                description = currentDesc,
                                startUtcMillis = currentStart,
                                endUtcMillis = currentStop
                            )
                            val nowProgram = pickNow(nowCandidates[channel.id], program, nowUtc)
                            nowCandidates[channel.id] = nowProgram
                            if (program.startUtcMillis > nowUtc) {
                                val future = upcomingCandidates.getOrPut(channel.id) { mutableListOf() }
                                addUpcomingCandidate(future, program, limit = 8)
                            } else if (program.endUtcMillis <= nowUtc && program.endUtcMillis > recentCutoff) {
                                // Recently ended program – keep for the past-window in the EPG guide
                                val recent = recentCandidates.getOrPut(channel.id) { mutableListOf() }
                                if (recent.size < 6) recent.add(program)
                            }
                        }
                        currentChannelKey = null
                        currentStart = 0L
                        currentStop = 0L
                        currentTitle = null
                        currentDesc = null
                    }
                }
            }
        }

        parser.parse(input, handler)

        val result = channels.associate { channel ->
            val future = upcomingCandidates[channel.id].orEmpty()
            val recent = recentCandidates[channel.id].orEmpty().sortedBy { it.startUtcMillis }
            channel.id to IptvNowNext(
                now = nowCandidates[channel.id],
                next = future.getOrNull(0),
                later = future.getOrNull(1),
                upcoming = future,
                recent = recent
            )
        }
        return result
    }

    private fun pickNow(existing: IptvProgram?, candidate: IptvProgram, nowUtcMillis: Long): IptvProgram? {
        if (!candidate.isLive(nowUtcMillis)) return existing
        if (existing == null) return candidate
        return if (candidate.startUtcMillis >= existing.startUtcMillis) candidate else existing
    }

    private fun addUpcomingCandidate(
        upcoming: MutableList<IptvProgram>,
        candidate: IptvProgram,
        limit: Int
    ) {
        val duplicate = upcoming.any {
            it.startUtcMillis == candidate.startUtcMillis &&
                it.endUtcMillis == candidate.endUtcMillis &&
                it.title.equals(candidate.title, ignoreCase = true)
        }
        if (duplicate) return

        val insertIndex = upcoming.indexOfFirst {
            candidate.startUtcMillis < it.startUtcMillis ||
                (candidate.startUtcMillis == it.startUtcMillis && candidate.endUtcMillis > it.endUtcMillis)
        }
        if (insertIndex >= 0) {
            upcoming.add(insertIndex, candidate)
        } else {
            upcoming.add(candidate)
        }
        while (upcoming.size > limit) {
            upcoming.removeAt(upcoming.lastIndex)
        }
    }

    private fun hasAnyProgramData(nowNext: Map<String, IptvNowNext>): Boolean {
        if (nowNext.isEmpty()) return false
        return nowNext.values.any { item ->
            item.now != null || item.next != null || item.later != null || item.upcoming.isNotEmpty()
        }
    }

    private fun parseXmlTvDate(rawValue: String?): Long {
        if (rawValue.isNullOrBlank()) return 0L
        val value = rawValue.trim()

        return runCatching {
            OffsetDateTime.parse(value, XMLTV_OFFSET_FORMATTER).toInstant().toEpochMilli()
        }.recoverCatching {
            val local = LocalDateTime.parse(value.take(14), XMLTV_LOCAL_FORMATTER)
            local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun buildChannelId(streamUrl: String, epgId: String?): String {
        val normalizedEpg = normalizeChannelKey(epgId ?: "")
        return if (normalizedEpg.isNotBlank()) {
            "epg:$normalizedEpg"
        } else {
            "url:${streamUrl.trim()}"
        }
    }

    private fun extractChannelName(metadata: String?): String {
        if (metadata.isNullOrBlank()) return "Unknown Channel"
        val idx = metadata.indexOf(',')
        return if (idx >= 0 && idx < metadata.lastIndex) {
            metadata.substring(idx + 1).trim().ifBlank { "Unknown Channel" }
        } else {
            "Unknown Channel"
        }
    }

    private fun extractAttr(metadata: String?, attr: String): String? {
        if (metadata.isNullOrBlank()) return null
        val source = metadata
        val key = "$attr="
        val startIndex = source.indexOf(key, ignoreCase = true)
        if (startIndex < 0) return null

        var valueStart = startIndex + key.length
        while (valueStart < source.length && source[valueStart].isWhitespace()) {
            valueStart++
        }
        if (valueStart >= source.length) return null

        val quote = source[valueStart]
        val raw = if (quote == '"' || quote == '\'') {
            var i = valueStart + 1
            while (i < source.length) {
                val ch = source[i]
                val escaped = i > valueStart + 1 && source[i - 1] == '\\'
                if (ch == quote && !escaped) break
                i++
            }
            source.substring(valueStart + 1, i.coerceAtMost(source.length))
        } else {
            var i = valueStart
            while (i < source.length) {
                val ch = source[i]
                if (ch.isWhitespace() || ch == ',') break
                i++
            }
            source.substring(valueStart, i.coerceAtMost(source.length))
        }

        // Handle malformed IPTV provider values such as tvg-name=\'VALUE\'.
        val normalized = raw
            .trim()
            .removePrefix("\\'")
            .removeSuffix("\\'")
            .removePrefix("\\\"")
            .removeSuffix("\\\"")
            .trim('"', '\'')
            .trim()
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun normalizeChannelKey(value: String): String = value.trim().lowercase(Locale.US)

    private fun normalizeLooseKey(value: String): String {
        return normalizeChannelKey(value).replace(Regex("[^a-z0-9]"), "")
    }

    private fun buildChannelKeyLookup(channels: List<IptvChannel>): Map<String, IptvChannel> {
        val map = LinkedHashMap<String, IptvChannel>(channels.size * 4)
        channels.forEach { channel ->
            val candidates = mutableSetOf<String>()
            candidates += normalizeChannelKey(channel.name)
            candidates += normalizeLooseKey(channel.name)
            candidates += normalizeLooseKey(stripQualitySuffixes(channel.name))

            channel.epgId?.takeIf { it.isNotBlank() }?.let { epgId ->
                candidates += normalizeChannelKey(epgId)
                candidates += normalizeLooseKey(epgId)
            }

            extractAttr(channel.rawTitle, "tvg-name")?.takeIf { it.isNotBlank() }?.let { tvgName ->
                candidates += normalizeChannelKey(tvgName)
                candidates += normalizeLooseKey(tvgName)
                candidates += normalizeLooseKey(stripQualitySuffixes(tvgName))
            }

            candidates.filter { it.isNotBlank() }.forEach { key ->
                map.putIfAbsent(key, channel)
            }
        }
        return map
    }

    private fun resolveXmlTvChannel(
        xmlChannelKey: String,
        xmlChannelNameMap: Map<String, Set<String>>,
        keyLookup: Map<String, IptvChannel>
    ): IptvChannel? {
        val normalized = normalizeChannelKey(xmlChannelKey)
        val normalizedLoose = normalizeLooseKey(xmlChannelKey)

        keyLookup[normalized]?.let { return it }
        keyLookup[normalizedLoose]?.let { return it }
        keyLookup[normalizeLooseKey(stripQualitySuffixes(xmlChannelKey))]?.let { return it }

        val names = xmlChannelNameMap[normalized].orEmpty()
        names.forEach { display ->
            keyLookup[display]?.let { return it }
            keyLookup[normalizeLooseKey(display)]?.let { return it }
            keyLookup[normalizeLooseKey(stripQualitySuffixes(display))]?.let { return it }
        }
        return null
    }

    private fun stripQualitySuffixes(value: String): String {
        return value
            .lowercase(Locale.US)
            .replace(Regex("\\b(hd|fhd|uhd|sd|4k|hevc|x265|x264|h264|h265)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun prepareInputStream(source: InputStream, url: String): InputStream {
        val buffered = BufferedInputStream(source)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val isGzipMagic = b1 == 0x1f && b2 == 0x8b
        return if (isGzipMagic || url.lowercase(Locale.US).endsWith(".gz")) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }

    private fun looksLikeM3u(source: InputStream): Boolean {
        source.mark(1024)
        val bytes = ByteArray(1024)
        val read = source.read(bytes)
        source.reset()
        if (read <= 0) return false
        val text = String(bytes, 0, read, StandardCharsets.UTF_8).trimStart()
        return text.startsWith("#EXTM3U", ignoreCase = true)
    }

    private fun looksLikeXmlTv(source: InputStream): Boolean {
        source.mark(2048)
        val bytes = ByteArray(2048)
        val read = source.read(bytes)
        source.reset()
        if (read <= 0) return false
        val text = String(bytes, 0, read, StandardCharsets.UTF_8).trimStart()
        return text.startsWith("<?xml", ignoreCase = true) || text.startsWith("<tv", ignoreCase = true)
    }

    private fun cacheFile(): File {
        val dir = File(context.filesDir, "iptv_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${profileManager.getProfileIdSync()}_iptv_cache.json")
    }

    private fun cleanupStaleEpgTempFiles(maxAgeMs: Long = 3 * 60_000L) {
        runCatching {
            val now = System.currentTimeMillis()
            context.cacheDir.listFiles()?.forEach { file ->
                if (!file.name.startsWith("epg_") || !file.name.endsWith(".xml")) return@forEach
                val age = now - file.lastModified()
                if (age > maxAgeMs) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    private fun cleanupIptvCacheDirectory() {
        runCatching {
            val dir = File(context.filesDir, "iptv_cache")
            if (!dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (!file.name.endsWith("_iptv_cache.json")) return@forEach
                if (file.length() > MAX_IPTV_CACHE_BYTES * 2) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    private fun pruneOversizedIptvCacheFile() {
        runCatching {
            val file = cacheFile()
            if (!file.exists()) return
            if (file.length() > MAX_IPTV_CACHE_BYTES * 2) {
                file.delete()
            }
        }
    }

    private fun buildConfigSignature(config: IptvConfig): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val raw = "${config.m3uUrl.trim()}|${config.epgUrl.trim()}"
        return digest.digest(raw.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeCache(
        config: IptvConfig,
        channels: List<IptvChannel>,
        nowNext: Map<String, IptvNowNext>,
        loadedAtMs: Long
    ) {
        runCatching {
            val compactChannels = channels.map { channel ->
                // Keep cache lean: strip raw EXTINF metadata but preserve logos for cold-start display.
                channel.copy(
                    rawTitle = channel.name
                )
            }
            val compactNowNext = nowNext.mapValues { (_, value) ->
                IptvNowNext(
                    now = value.now?.let { IptvProgram(it.title, null, it.startUtcMillis, it.endUtcMillis) },
                    next = value.next?.let { IptvProgram(it.title, null, it.startUtcMillis, it.endUtcMillis) },
                    later = null,
                    upcoming = emptyList()
                )
            }
            val payload = IptvCachePayload(
                channels = compactChannels,
                nowNext = compactNowNext,
                loadedAtEpochMs = loadedAtMs,
                configSignature = buildConfigSignature(config)
            )
            val json = gson.toJson(payload)
            if (json.toByteArray(StandardCharsets.UTF_8).size <= MAX_IPTV_CACHE_BYTES) {
                cacheFile().writeText(json, StandardCharsets.UTF_8)
            } else {
                // Emergency fallback: store channels only.
                val reduced = payload.copy(nowNext = emptyMap())
                cacheFile().writeText(gson.toJson(reduced), StandardCharsets.UTF_8)
            }
        }
    }

    private fun readCache(config: IptvConfig): IptvCachePayload? {
        return runCatching {
            val file = cacheFile()
            if (!file.exists()) return null
            if (file.length() > MAX_IPTV_CACHE_BYTES * 2) {
                runCatching { file.delete() }
                return null
            }
            val text = file.readText(StandardCharsets.UTF_8)
            if (text.isBlank()) return null
            val payload = gson.fromJson(text, IptvCachePayload::class.java) ?: return null
            if (payload.configSignature != buildConfigSignature(config)) return null
            if (payload.channels.isEmpty()) return null
            payload
        }.getOrNull()
    }

    private fun encryptConfigValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith(ENC_PREFIX)) return trimmed
        return runCatching { ENC_PREFIX + encryptAesGcm(trimmed) }.getOrDefault(trimmed)
    }

    private fun decryptConfigValue(stored: String): String {
        val trimmed = stored.trim()
        if (trimmed.isBlank()) return ""
        if (!trimmed.startsWith(ENC_PREFIX)) return trimmed
        val payload = trimmed.removePrefix(ENC_PREFIX)
        return runCatching { decryptAesGcm(payload) }.getOrElse { "" }
    }

    private fun encryptAesGcm(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivPart = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataPart = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$ivPart:$dataPart"
    }

    private fun decryptAesGcm(payload: String): String {
        val split = payload.split(":", limit = 2)
        require(split.size == 2) { "Invalid encrypted payload" }
        val iv = Base64.decode(split[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(split[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(encrypted)
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(CONFIG_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            CONFIG_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private class ProgressInputStream(
        source: InputStream,
        private val onBytesRead: (Long) -> Unit
    ) : FilterInputStream(source) {
        private var totalRead: Long = 0L
        private var lastEmit: Long = 0L
        private val emitStepBytes = 8L * 1024L * 1024L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) trackRead(1)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read > 0) trackRead(read.toLong())
            return read
        }

        private fun trackRead(bytes: Long) {
            totalRead += bytes
            if (totalRead - lastEmit >= emitStepBytes) {
                lastEmit = totalRead
                onBytesRead(totalRead)
            }
        }
    }

    private companion object {
        const val ENC_PREFIX = "encv1:"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CONFIG_KEY_ALIAS = "arvio_iptv_config_v1"
        const val MAX_IPTV_CACHE_BYTES = 25L * 1024L * 1024L

        val XMLTV_LOCAL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        val XMLTV_OFFSET_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .optionalStart()
            .appendLiteral(' ')
            .appendPattern("XX")
            .optionalEnd()
            .toFormatter(Locale.US)
    }
}
