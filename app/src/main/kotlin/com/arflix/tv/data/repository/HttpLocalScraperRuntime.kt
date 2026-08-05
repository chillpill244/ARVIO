package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonBehaviorHints
import com.arflix.tv.data.model.AddonManifest
import com.arflix.tv.data.model.AddonResource
import com.arflix.tv.data.model.ProxyHeaders
import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class HttpLocalScraperInstallCandidate(
    val name: String,
    val version: String,
    val description: String,
    val logo: String?,
    val manifest: AddonManifest,
    val transportUrl: String
)

@Singleton
class HttpLocalScraperRuntime @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tmdbApi: TmdbApi
) {
    private val gson = Gson()
    private val manifestCache = mutableMapOf<String, HttpScraperManifest>()
    private val tmdbIdCache = mutableMapOf<String, Int?>()
    @Volatile private var newTvApiUrl: String = ""
    @Volatile private var toonstreamDomain: String = ""
    @Volatile private var toonstreamDomainCachedAt: Long = 0L
    @Volatile private var fourKHDHubDomain: String = ""
    @Volatile private var fourKHDHubHubcloud: String = ""
    @Volatile private var fourKHDHubDomainCachedAt: Long = 0L
    private val noRedirectClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun fetchInstallCandidate(
        url: String,
        customName: String?
    ): HttpLocalScraperInstallCandidate? = withContext(Dispatchers.IO) {
        val manifestUrl = manifestUrlFor(url)
        val manifest = fetchManifest(manifestUrl) ?: return@withContext null
        val httpScrapers = manifest.scrapers.filter { it.isHttpOnlyEnabled() }
        if (httpScrapers.isEmpty()) return@withContext null

        val stableId = "http.local.${shortHash(manifestUrl)}"
        val addonManifest = AddonManifest(
            id = stableId,
            name = sanitizeProviderLabel(customName?.trim()?.takeIf { it.isNotBlank() } ?: manifest.name),
            version = manifest.version,
            description = "HTTP local scraper bundle (${httpScrapers.size} HTTP providers)",
            types = listOf("movie", "series"),
            resources = listOf(
                AddonResource(
                    name = "stream",
                    types = listOf("movie", "series"),
                    idPrefixes = listOf("tt")
                )
            ),
            behaviorHints = AddonBehaviorHints(p2p = false)
        )
        HttpLocalScraperInstallCandidate(
            name = addonManifest.name,
            version = manifest.version,
            description = addonManifest.description,
            logo = httpScrapers.firstNotNullOfOrNull { it.logo?.takeIf(String::isNotBlank) },
            manifest = addonManifest,
            transportUrl = manifestUrl.substringBeforeLast('/', missingDelimiterValue = manifestUrl)
        )
    }

    fun canHandle(addon: Addon): Boolean {
        val manifestId = addon.manifest?.id ?: return false
        return manifestId.startsWith(HTTP_LOCAL_MANIFEST_PREFIX) ||
            manifestId.startsWith(LEGACY_LOCAL_MANIFEST_PREFIX)
    }

    suspend fun resolveMovieStreams(
        addon: Addon,
        imdbId: String,
        title: String,
        year: Int?
    ): List<StreamSource> {
        val manifest = addon.url?.let { fetchManifest(manifestUrlFor(it)) } ?: return emptyList()
        val tmdbId = resolveTmdbId(imdbId, mediaType = "movie") ?: return emptyList()
        return resolveHttpStreams(
            addon = addon,
            manifest = manifest,
            tmdbId = tmdbId,
            mediaType = "movie",
            season = null,
            episode = null,
            fallbackTitle = title,
            fallbackYear = year
        )
    }

    suspend fun resolveEpisodeStreams(
        addon: Addon,
        imdbId: String,
        season: Int,
        episode: Int,
        tmdbId: Int?,
        title: String
    ): List<StreamSource> {
        val manifest = addon.url?.let { fetchManifest(manifestUrlFor(it)) } ?: return emptyList()
        val resolvedTmdbId = tmdbId ?: resolveTmdbId(imdbId, mediaType = "tv") ?: return emptyList()
        return resolveHttpStreams(
            addon = addon,
            manifest = manifest,
            tmdbId = resolvedTmdbId,
            mediaType = "tv",
            season = season,
            episode = episode,
            fallbackTitle = title,
            fallbackYear = null
        )
    }

    private suspend fun resolveHttpStreams(
        addon: Addon,
        manifest: HttpScraperManifest,
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<StreamSource> = coroutineScope {
        val providers = manifest.scrapers
            .filter { it.isHttpOnlyEnabled() }
            .map { it.id.lowercase(Locale.US) }
            .toSet()

        val jobs = buildList {
            if ("multivid" in providers || "videasy" in providers) {
                add(async(Dispatchers.IO) { resolveVidEasy(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
            }
            if ("multivid" in providers || "vidlink" in providers) {
                add(async(Dispatchers.IO) { resolveVidLink(tmdbId, mediaType, season, episode) })
            }
            if ("multivid" in providers) {
                add(async(Dispatchers.IO) { resolveVidMody(tmdbId, mediaType, season, episode) })
            }
            if ("multivid" in providers || "vidsrc" in providers || "vixsrc" in providers) {
                add(async(Dispatchers.IO) { resolveVidSrc(tmdbId, mediaType, season, episode) })
            }
            if ("rgshows" in providers) {
                add(async(Dispatchers.IO) { resolveRgShows(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
            }
            if ("playimdb" in providers || "playimdb_series" in providers) {
                val movieEnabled = "playimdb" in providers || mediaType != "movie"
                val seriesEnabled = "playimdb_series" in providers || mediaType != "tv"
                if ((mediaType == "movie" && movieEnabled) || (mediaType == "tv" && seriesEnabled)) {
                    add(async(Dispatchers.IO) { resolvePlayImdb(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
                }
            }
            if ("dooflix" in providers) {
                add(async(Dispatchers.IO) { resolveDooFlix(tmdbId, mediaType, season, episode) })
            }
            if ("fmovies" in providers) {
                add(async(Dispatchers.IO) { resolveFMovies(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
            }
            if ("brazucaplay" in providers) {
                add(async(Dispatchers.IO) { resolveBrazucaPlay(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
            }
            if ("netmirror" in providers) {
                add(async(Dispatchers.IO) { resolveNetMirror(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
            }
            if ("toonstream" in providers) {
                add(async(Dispatchers.IO) {
                    runCatching { resolveToonstream(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) }
                        .getOrDefault(emptyList())
                })
            }
            if ("fourkhdhub" in providers) {
                add(async(Dispatchers.IO) {
                    runCatching { resolveFourKHDHub(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) }
                        .getOrDefault(emptyList())
                })
            }
            if ("anidb" in providers) {
                add(async(Dispatchers.IO) {
                    runCatching { resolveAniDb(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) }
                        .getOrDefault(emptyList())
                })
            }
            if ("hdghartv" in providers) {
                add(async(Dispatchers.IO) {
                    runCatching { resolveHdGharTv(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) }
                        .getOrDefault(emptyList())
                })
            }
            if ("castle" in providers) {
                add(async(Dispatchers.IO) {
                    runCatching { resolveCastle(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) }
                        .getOrDefault(emptyList())
                })
            }
        }
        jobs.awaitAll()
            .flatten()
            .filter { it.url.startsWith("http://", ignoreCase = true) || it.url.startsWith("https://", ignoreCase = true) }
            .filterNot { it.url.startsWith("magnet:", ignoreCase = true) || it.url.contains("btih:", ignoreCase = true) }
            .distinctBy { it.url }
            .take(50)
            .map { stream -> stream.toStreamSource(addon) }
    }

    private suspend fun resolveVidEasy(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val servers = listOf(
            VideasyServer("VIDEASY", "Neon", "https://api.videasy.net/myflixerzupcloud/sources-with-title"),
            VideasyServer("VIDEASY", "Yoru", "https://api.videasy.net/cdn/sources-with-title", moviesOnly = true),
            VideasyServer("VIDEASY", "Cypher", "https://api.videasy.net/moviebox/sources-with-title"),
            VideasyServer("VIDEASY", "Reyna", "https://api.videasy.net/primewire/sources-with-title"),
            VideasyServer("VIDEASY", "Omen", "https://api.videasy.net/onionplay/sources-with-title"),
            VideasyServer("VIDEASY", "Breach", "https://api.videasy.net/m4uhd/sources-with-title"),
            VideasyServer("VIDEASY", "Ghost", "https://api.videasy.net/primesrcme/sources-with-title"),
            VideasyServer("VIDEASY", "Sage", "https://api.videasy.net/1movies/sources-with-title"),
            VideasyServer("VIDEASY", "Vyse", "https://api.videasy.net/hdmovie/sources-with-title"),
            VideasyServer("VIDEASY", "Raze", "https://api.videasy.net/superflix/sources-with-title")
        )
        return resolveVideasyServers(tmdbId, details, mediaType, season, episode, servers, "VIDEASY")
    }

    private suspend fun resolveFMovies(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val servers = listOf(
            VideasyServer("FMovies", "Yoru Original", "https://api.videasy.net/cdn/sources-with-title", moviesOnly = true),
            VideasyServer("FMovies", "Vyse Hindi", "https://api.videasy.net/hdmovie/sources-with-title")
        )
        return resolveVideasyServers(tmdbId, details, mediaType, season, episode, servers, "FMovies")
    }

    private suspend fun resolveBrazucaPlay(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val servers = listOf(
            VideasyServer("BrazucaPlay", "Cuevana Latino", "https://api2.videasy.net/cuevana/sources-with-title"),
            VideasyServer("BrazucaPlay", "Superflix PT", "https://api.videasy.net/superflix/sources-with-title"),
            VideasyServer("BrazucaPlay", "Overflix PT", "https://api2.videasy.net/overflix/sources-with-title"),
            VideasyServer("BrazucaPlay", "VisaoCine PT", "https://api.videasy.net/visioncine/sources-with-title")
        )
        return resolveVideasyServers(tmdbId, details, mediaType, season, episode, servers, "BrazucaPlay")
    }

    private suspend fun resolveVideasyServers(
        tmdbId: Int,
        details: HttpScraperTmdbDetails,
        mediaType: String,
        season: Int?,
        episode: Int?,
        servers: List<VideasyServer>,
        providerName: String
    ): List<HttpResolvedStream> {
        return coroutineScope {
            servers.map { server ->
                async(Dispatchers.IO) {
                    runCatching {
                        if (mediaType == "tv" && server.moviesOnly) return@runCatching emptyList<HttpResolvedStream>()
                        var url = "${server.endpoint}?title=${details.title.urlEncode()}" +
                            "&mediaType=${details.mediaType}&year=${details.year.orEmpty()}" +
                            "&tmdbId=$tmdbId&imdbId=${details.imdbId.orEmpty()}"
                        if (mediaType == "tv") {
                            url += "&seasonId=${season ?: 1}&episodeId=${episode ?: 1}"
                        }
                        val encrypted = getText(url, VIDEASY_HEADERS).takeIf { it.length > 20 && !it.startsWith("<!") }
                            ?: return@runCatching emptyList()
                        val decrypted = postJson(
                            url = "https://enc-dec.app/api/dec-videasy",
                            body = """{"text":${gson.toJson(encrypted)},"id":"$tmdbId"}"""
                        )
                        val result = decrypted?.getObject("result") ?: decrypted
                        (result?.getArray("sources")?.toList().orEmpty()).mapNotNull { source: JsonElement ->
                            val obj = source.asJsonObjectOrNull() ?: return@mapNotNull null
                            val streamUrl = obj.string("url") ?: return@mapNotNull null
                            HttpResolvedStream(
                                provider = "${server.provider} ${server.name}",
                                title = "$providerName ${server.name} ${obj.string("quality").orEmpty()}".trim(),
                                url = streamUrl,
                                quality = obj.string("quality") ?: "Auto",
                                headers = mapOf(
                                    "Referer" to "https://player.videasy.net/",
                                    "Origin" to "https://player.videasy.net",
                                    "User-Agent" to USER_AGENT
                                )
                            )
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun resolveVidMody(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): List<HttpResolvedStream> = runCatching {
        val meta = fetchTmdbDetails(tmdbId, mediaType, "", null)
        val imdbId = meta.imdbId ?: return@runCatching emptyList<HttpResolvedStream>()
        val targetUrl = if (mediaType == "tv") {
            val seasonText = "s${season ?: 1}"
            val episodeText = "e${(episode ?: 1).toString().padStart(2, '0')}"
            "https://vidmody.com/vs/$imdbId/$seasonText/$episodeText#.m3u8"
        } else {
            "https://vidmody.com/vs/$imdbId#.m3u8"
        }
        val probeUrl = targetUrl.replace("#.m3u8", "")
        val request = Request.Builder()
            .url(probeUrl)
            .head()
            .headers(okhttp3.Headers.headersOf("Referer", "https://vidmody.com/", "User-Agent", USER_AGENT))
            .build()
        val ok = withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response -> response.isSuccessful }
        }
        if (!ok) return@runCatching emptyList<HttpResolvedStream>()
        listOf(
            HttpResolvedStream(
                provider = "VidMody",
                title = "VidMody Auto",
                url = targetUrl,
                quality = "Auto",
                headers = mapOf("Referer" to "https://vidmody.com/", "User-Agent" to USER_AGENT)
            )
        )
    }.getOrDefault(emptyList())

    private suspend fun resolveVidLink(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): List<HttpResolvedStream> = runCatching {
        val encrypted = getJson("https://enc-dec.app/api/enc-vidlink?text=${tmdbId.toString().urlEncode()}")
            ?.string("result")
            ?: return@runCatching emptyList()
        val url = if (mediaType == "tv") {
            "https://vidlink.pro/api/b/tv/$encrypted/${season ?: 1}/${episode ?: 1}?multiLang=0"
        } else {
            "https://vidlink.pro/api/b/movie/$encrypted?multiLang=0"
        }
        val payload = getJson(url, VIDLINK_HEADERS) ?: return@runCatching emptyList()
        val playlist = payload.getObject("stream")?.string("playlist") ?: return@runCatching emptyList()
        listOf(
            HttpResolvedStream(
                provider = "VidLink",
                title = "VidLink Primary",
                url = playlist,
                quality = "Auto",
                headers = mapOf("Referer" to "https://vidlink.pro/", "Origin" to "https://vidlink.pro")
            )
        )
    }.getOrDefault(emptyList())

    private suspend fun resolveRgShows(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> = runCatching {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val headers = mapOf(
            "Referer" to "https://www.rgshows.ru/",
            "Origin" to "https://www.rgshows.ru",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 15; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/146.0.7680.177 Mobile Safari/537.36",
            "Accept" to "*/*",
            "Accept-Encoding" to "identity;q=1, *;q=0"
        )
        val url = if (mediaType == "tv") {
            "https://api.rgshows.ru/main/tv/$tmdbId/${season ?: 1}/${episode ?: 1}"
        } else {
            "https://api.rgshows.ru/main/movie/$tmdbId"
        }
        val streamUrl = getJson(url, headers)
            ?.getObject("stream")
            ?.string("url")
            ?.takeIf { it.startsWith("http", ignoreCase = true) && !it.contains("vidzee.wtf", ignoreCase = true) }
            ?: return@runCatching emptyList()
        listOf(
            HttpResolvedStream(
                provider = "RGShows",
                title = if (mediaType == "tv") {
                    "${details.title} S${(season ?: 1).toString().padStart(2, '0')}E${(episode ?: 1).toString().padStart(2, '0')}"
                } else {
                    "${details.title} ${details.year?.let { "($it)" }.orEmpty()}".trim()
                },
                url = streamUrl,
                quality = "Auto",
                headers = headers
            )
        )
    }.getOrDefault(emptyList())

    private suspend fun resolvePlayImdb(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> = runCatching {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val imdbId = details.imdbId ?: return@runCatching emptyList<HttpResolvedStream>()
        val baseUrl = "https://vsembed.ru"
        val landingUrl = "$baseUrl/embed/$imdbId/"
        val landingHtml = getText(landingUrl)
        var targetUrl = landingUrl
        if (mediaType == "tv") {
            val divRegex = DIV_EP_REGEX
            divRegex.findAll(landingHtml).firstOrNull { match ->
                val div = match.value
                div.contains("data-s=\"${season ?: 1}\"", ignoreCase = true) &&
                    div.contains("data-e=\"${episode ?: 1}\"", ignoreCase = true)
            }?.value?.let { div ->
                DATA_IFRAME_REGEX.find(div)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { iframe -> targetUrl = if (iframe.startsWith("/")) "$baseUrl$iframe" else iframe }
            }
        }
        val pageHtml = getText(targetUrl, mapOf("Referer" to "$baseUrl/"))
        val iframeSrc = IFRAME_PLAYER_REGEX.find(pageHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: IFRAME_SRC_REGEX.find(pageHtml)
                ?.groupValues
                ?.getOrNull(1)
            ?: return@runCatching emptyList()
        val iframeUrl = when {
            iframeSrc.startsWith("//") -> "https:$iframeSrc"
            iframeSrc.startsWith("/") -> "$baseUrl$iframeSrc"
            else -> iframeSrc
        }
        decryptCloudnestraStreams(
            provider = "PlayIMDb",
            title = details.title,
            sourceUrl = iframeUrl,
            referer = targetUrl
        )
    }.getOrDefault(emptyList())

    private suspend fun resolveDooFlix(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): List<HttpResolvedStream> = runCatching {
        val requestUrl = if (mediaType == "tv") {
            "https://panel.watchkaroabhi.com/api/3/tv/$tmdbId/season/${season ?: 1}/episode/${episode ?: 1}/links?api_key=qNhKLJiZVyoKdi9NCQGz8CIGrpUijujE"
        } else {
            "https://panel.watchkaroabhi.com/api/3/movie/$tmdbId/links?api_key=qNhKLJiZVyoKdi9NCQGz8CIGrpUijujE"
        }
        val apiHeaders = mapOf(
            "X-Package-Name" to "com.king.moja",
            "User-Agent" to "dooflix",
            "X-App-Version" to "305"
        )
        val links = getJson(requestUrl, apiHeaders)?.getArray("links")?.toList().orEmpty()
        links.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val linkUrl = obj.string("url") ?: return@mapNotNull null
            val streamUrl = resolveRedirectUrl(
                url = linkUrl,
                headers = mapOf("Referer" to "https://molop.art/", "User-Agent" to "dooflix")
            ) ?: return@mapNotNull null
            HttpResolvedStream(
                provider = "DooFlix",
                title = "DooFlix ${obj.string("host").orEmpty()}".trim(),
                url = streamUrl,
                quality = "Auto",
                headers = mapOf("Referer" to "https://molop.art/", "User-Agent" to "dooflix")
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun resolveNetMirror(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> = runCatching {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val apiBase = resolveNewTvApiUrl()
        val platforms = listOf(
            NetMirrorPlatform("Netflix", "nf", "/newtv/search.php", "/newtv/post.php", "/newtv/episodes.php", "/newtv/player.php"),
            NetMirrorPlatform("PrimeVideo", "pv", "/newtv/search.php", "/newtv/post.php", "/newtv/episodes.php", "/newtv/player.php"),
            NetMirrorPlatform("Hotstar", "hs", "/newtv/search.php", "/newtv/post.php", "/newtv/episodes.php", "/newtv/player.php")
        )
        coroutineScope {
            platforms.map { platform ->
                async(Dispatchers.IO) {
                    runCatching { fetchNewTvPlatform(platform, details.title, mediaType, season, episode, apiBase) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
    }.getOrDefault(emptyList())

    // ── Toonstream ──────────────────────────────────────────────────────────────

    private suspend fun resolveToonStreamDomain(): String {
        val now = System.currentTimeMillis()
        if (toonstreamDomain.isNotEmpty() && now - toonstreamDomainCachedAt < 3_600_000L)
            return toonstreamDomain
        val data = getJson(
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json",
            mapOf("User-Agent" to USER_AGENT)
        )
        val domain = data?.string("toonstream") ?: "https://toonstream.one"
        toonstreamDomain = domain
        toonstreamDomainCachedAt = now
        return domain
    }

    private suspend fun resolveToonstream(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val domain = resolveToonStreamDomain()
        val searchResults = searchToonstream(domain, details.title)
        val typedResults = searchResults.filter { 
            if (mediaType == "tv") it.type == "series" else it.type == "movie" 
        }
        val ranked = rankToonstream(typedResults, details.title)

        for (candidate in ranked.take(3)) {
            val pageUrl = if (mediaType == "tv") {
                val eps = getToonstreamSeasonEpisodes(domain, candidate.url, season ?: 1)
                eps.find { it.season == (season ?: 1) && it.episode == (episode ?: 1) }?.url ?: continue
            } else {
                candidate.url
            }

            val videoLinks = runCatching { getToonstreamVideoLinks(pageUrl) }.getOrNull() ?: continue
            val orderedLinks = videoLinks.sortedByDescending { l ->
                if ("as-cdn" in l || "awstream" in l || "zephyrflick" in l) 1 else 0
            }
            for (link in orderedLinks) {
                val stream = runCatching { extractToonstreamStream(link) }.getOrNull() ?: continue
                val label = if (mediaType == "tv") {
                    val s = (season ?: 1).toString().padStart(2, '0')
                    val e = (episode ?: 1).toString().padStart(2, '0')
                    "${details.title} S${s}E${e} · Toonstream"
                } else {
                    "${details.title} · Toonstream"
                }
                return listOf(stream.copy(title = label))
            }
        }
        return emptyList()
    }

    private suspend fun searchToonstream(domain: String, query: String): List<ToonstreamResult> {
        val html = runCatching {
            getText("$domain/s?q=${query.urlEncode()}", mapOf("User-Agent" to USER_AGENT))
        }.getOrNull() ?: return emptyList()
        val doc = org.jsoup.Jsoup.parse(html)
        val results = mutableListOf<ToonstreamResult>()
        doc.select("article").forEach { art ->
            val href = art.selectFirst("a")?.attr("href") ?: return@forEach
            val title = art.selectFirst("h2")?.text()?.trim()
                ?: art.selectFirst(".title")?.text()?.trim()
                ?: art.selectFirst("img")?.attr("alt")?.trim() ?: return@forEach
            var type = "movie"
            if (href.contains("/series/")) type = "series"
            val url = if (href.startsWith("http")) href else "$domain$href"
            if (title.isNotBlank()) {
                results.add(ToonstreamResult(url, title, type))
            }
        }
        return results
    }

    private fun rankToonstream(results: List<ToonstreamResult>, title: String): List<ToonstreamResult> {
        val want = title.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()
        fun norm(s: String) = s.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()
        return results.filter { norm(it.title) == want } +
               results.filter { norm(it.title) != want && norm(it.title).startsWith(want) }
    }

    private suspend fun getToonstreamSeasonEpisodes(
        domain: String,
        seriesUrl: String,
        targetSeason: Int
    ): List<ToonstreamEpisode> {
        val html = runCatching { 
            getText("$seriesUrl/season/$targetSeason", mapOf("User-Agent" to USER_AGENT)) 
        }.getOrNull() ?: return emptyList()
        val doc = org.jsoup.Jsoup.parse(html)
        val episodes = mutableListOf<ToonstreamEpisode>()
        
        doc.select("article").forEach { art ->
            val aTag = art.closest("a") ?: art.selectFirst("a") ?: return@forEach
            val href = aTag.attr("href").ifBlank { return@forEach }
            val numEpi = art.selectFirst(".num-epi")?.text()?.trim() ?: ""
            val parts = numEpi.split("x")
            val s = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
            val e = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            val epUrl = if (href.startsWith("http")) href else "$domain$href"
            episodes.add(ToonstreamEpisode(epUrl, s, e))
        }
        
        if (episodes.isEmpty()) {
            doc.select("a").forEach { aTag ->
                val href = aTag.attr("href").ifBlank { return@forEach }
                val numEpi = aTag.selectFirst(".num-epi")?.text()?.trim() ?: ""
                if (numEpi.isNotBlank()) {
                    val parts = numEpi.split("x")
                    val s = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                    val e = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                    val epUrl = if (href.startsWith("http")) href else "$domain$href"
                    episodes.add(ToonstreamEpisode(epUrl, s, e))
                }
            }
        }
        
        return episodes
    }

    private suspend fun getToonstreamVideoLinks(pageUrl: String): List<String> = withContext(Dispatchers.IO) {
        val html = runCatching { getText(pageUrl, mapOf("User-Agent" to USER_AGENT)) }.getOrNull() ?: return@withContext emptyList()
        val doc = org.jsoup.Jsoup.parse(html)

        val trembedUrls = doc.select("#aa-options iframe, .video-player iframe")
            .map { it.attr("data-src").ifBlank { it.attr("src") } }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("http")) it else "${java.net.URI(pageUrl).scheme}://${java.net.URI(pageUrl).host}$it" }

        val links = mutableListOf<String>()
        coroutineScope {
            trembedUrls.map { dataSrc ->
                async(Dispatchers.IO) {
                    if (dataSrc.contains("/embed/")) {
                        val innerHtml = runCatching { getText(dataSrc, mapOf("User-Agent" to USER_AGENT)) }.getOrNull() ?: return@async null
                        val innerFrame = org.jsoup.Jsoup.parse(innerHtml).selectFirst("iframe")
                        val src = innerFrame?.attr("src")?.ifBlank { innerFrame.attr("data-src") }
                        if (!src.isNullOrBlank()) {
                            if (src.startsWith("//")) "https:$src" else src
                        } else null
                    } else {
                        dataSrc
                    }
                }
            }.awaitAll().filterNotNull().forEach { links.add(it) }
        }
        links
    }

    private suspend fun extractToonstreamStream(url: String): HttpResolvedStream? = when {
        "awstream" in url || "zephyrflick" in url || "as-cdn" in url -> extractToonstreamAWSStream(url)
        "streamruby.com" in url || "rubystm.com" in url -> extractToonstreamStreamruby(url)
        else -> extractToonstreamM3u8Page(url)
    }

    private suspend fun extractToonstreamM3u8Page(url: String): HttpResolvedStream? {
        val text = runCatching { getText(url, mapOf("Referer" to url)) }.getOrNull() ?: return null
        val m3u8Regex = Regex("""(?:file|src|source)\s*[:=]\s*["'](https?://[^"']*\.m3u8[^"']*)["']""")
        val m3u8 = m3u8Regex.find(text)?.groupValues?.get(1) ?: return null
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: ""
        android.util.Log.d("Toonstream", "m3u8 found at $host: $m3u8")
        return HttpResolvedStream("Toonstream", host, m3u8, "Auto",
            mapOf("Referer" to url))
    }

    private suspend fun extractToonstreamAWSStream(url: String): HttpResolvedStream? {
        val hash = url.substringAfterLast("/").substringBefore("?")
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val origin = "${uri.scheme}://${uri.host}"
        val formBody = "hash=${java.net.URLEncoder.encode(hash, "UTF-8")}&r=${java.net.URLEncoder.encode(origin, "UTF-8")}"
        val request = Request.Builder()
            .url("$origin/player/index.php?data=$hash&do=getVideo")
            .post(formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("x-requested-with", "XMLHttpRequest")
            .addHeader("Referer", url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val responseText = withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { it.body?.string() }
        } ?: return null
        val data = runCatching { gson.fromJson(responseText, JsonObject::class.java) }.getOrNull() ?: return null
        val m3u8 = data.string("videoSource")?.takeIf { it.isNotBlank() } ?: return null
        android.util.Log.d("Toonstream", "AWS m3u8 at ${uri.host}: $m3u8")
        return HttpResolvedStream("Toonstream", uri.host, m3u8, "Auto")
    }

    private suspend fun extractToonstreamStreamruby(url: String): HttpResolvedStream? {
        val clean = url.replace(Regex("/e/(?=\\w)"), "/")
        val text = runCatching { getText(clean) }.getOrNull() ?: return null
        val m3u8 = Regex("""file:\s*['"](.*?\.m3u8.*?)['"]""").find(text)?.groupValues?.get(1) ?: return null
        return HttpResolvedStream("Toonstream", "Streamruby", m3u8, "Auto",
            mapOf("Referer" to "streamruby.com"))
    }

    // ── End Toonstream ───────────────────────────────────────────────────────────

    // ── 4KHDHub ──────────────────────────────────────────────────────────────────

    private fun rot13(value: String): String = buildString(value.length) {
        for (c in value) append(
            when (c) {
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                else -> c
            }
        )
    }

    private fun b64d(s: String): String = runCatching {
        android.util.Base64.decode(s, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
    }.getOrDefault("")

    private fun normTitle(s: String): String =
        s.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun absoluteUrl(domain: String, href: String): String {
        if (href.isBlank()) return ""
        if (href.startsWith("http")) return href
        if (href.startsWith("//")) return "https:$href"
        val base = domain.trimEnd('/')
        return if (href.startsWith("/")) "$base$href" else "$base/$href"
    }

    private suspend fun resolveFourKHDHubDomains(): Pair<String, String> {
        val now = System.currentTimeMillis()
        if (fourKHDHubDomain.isNotEmpty() && now - fourKHDHubDomainCachedAt < 3_600_000L)
            return fourKHDHubDomain to fourKHDHubHubcloud
        val data = getJson(
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json",
            mapOf("User-Agent" to USER_AGENT)
        )
        val main = data?.string("4khdhub") ?: "https://4khdhub.one"
        val hub = data?.string("hubcloud") ?: "https://hubcloud.foo"
        fourKHDHubDomain = main
        fourKHDHubHubcloud = hub
        fourKHDHubDomainCachedAt = now
        return main to hub
    }

    private suspend fun resolveFourKHDHub(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val (main, hub) = resolveFourKHDHubDomains()
        val ranked = rankFourK(searchFourK(main, details.title), details.title)

        val collected = mutableListOf<FourKStream>()
        val seen = mutableSetOf<String>()
        val wantYear = details.year?.toIntOrNull()

        for (candidate in ranked.take(5)) {
            val page = runCatching {
                loadFourKPage(candidate.url, mediaType, season ?: 1, episode ?: 1)
            }.getOrNull() ?: continue

            // Year gate — 4KHDHub lists same-named titles (e.g. 2010 animation vs 2025
            // live-action) under identical headings; only the year distinguishes them.
            if (wantYear != null && page.year != null && kotlin.math.abs(page.year - wantYear) > 1) continue

            val pageLinks = page.links
            if (pageLinks.isEmpty()) continue

            // Each download link is an independent host chain — resolve them concurrently so a
            // single slow host doesn't drag the whole resolver past the StreamFetch budget.
            val groups = coroutineScope {
                pageLinks.map { link ->
                    async(Dispatchers.IO) { runCatching { resolveFourKLink(link, hub) }.getOrNull().orEmpty() }
                }.awaitAll()
            }

            for (group in groups) {
                for (s in group) {
                    if (s.url.isBlank() || !seen.add(s.url)) continue
                    collected.add(s)
                }
            }
            if (collected.isNotEmpty()) break
        }

        // Best-first: higher resolution, then larger file size.
        return collected
            .sortedWith(compareByDescending<FourKStream> { fourKQualityRank(it.quality) }
                .thenByDescending { parseSizeGb(it.size) })
            .map { s ->
                HttpResolvedStream("4KHDHub", buildFourKTitle(s, mediaType, season, episode), s.url, s.quality)
            }
    }

    private suspend fun searchFourK(domain: String, query: String): List<FourKResult> {
        val html = runCatching { getText("$domain/?s=${query.urlEncode()}") }.getOrNull() ?: return emptyList()
        val doc = org.jsoup.Jsoup.parse(html)
        val seen = mutableSetOf<String>()
        val results = mutableListOf<FourKResult>()
        doc.select("div.card-grid a").forEach { a ->
            val href = absoluteUrl(domain, a.attr("href"))
            val title = a.selectFirst("h3")?.text()?.trim().orEmpty()
            if (href.isNotBlank() && title.isNotBlank() && seen.add(href)) results.add(FourKResult(href, title))
        }
        return results
    }

    private fun rankFourK(results: List<FourKResult>, title: String): List<FourKResult> {
        val want = normTitle(title)
        val exact = results.filter { normTitle(it.title) == want }
        val partial = results.filter {
            val n = normTitle(it.title)
            n != want && (n.contains(want) || want.contains(n))
        }
        return exact + partial
    }

    private suspend fun loadFourKPage(
        pageUrl: String,
        mediaType: String,
        season: Int,
        episode: Int
    ): FourKPage {
        val html = runCatching { getText(pageUrl) }.getOrNull() ?: return FourKPage(null, emptyList())
        val doc = org.jsoup.Jsoup.parse(html)
        val titleText = doc.selectFirst("h1.page-title")?.text().orEmpty()
        val year = (Regex("""\b(19|20)\d{2}\b""").find(titleText)?.value
            ?: Regex("""\b(19|20)\d{2}\b""").find(doc.select("div.mt-2 span").text())?.value)?.toIntOrNull()
        val links = if (mediaType == "tv") getFourKEpisodeLinks(doc, season, episode)
        else doc.select("div.download-item a").mapNotNull { it.attr("href").trim().ifBlank { null } }
        return FourKPage(year, links)
    }

    private fun getFourKEpisodeLinks(doc: org.jsoup.nodes.Document, season: Int, episode: Int): List<String> {
        val links = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        doc.select("div.episodes-list div.season-item").forEach { seasonEl ->
            val seasonText = seasonEl.select("div.episode-number").text()
            val s = Regex("S?([1-9][0-9]*)").find(seasonText)?.groupValues?.get(1)?.toIntOrNull()
            if (s != season) return@forEach
            seasonEl.select("div.episode-download-item").forEach { epEl ->
                val epText = epEl.select("span.badge-psa").text()
                val ep = Regex("Episode-0*([1-9][0-9]*)").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                if (ep != episode) return@forEach
                epEl.select("a").forEach { a ->
                    val href = a.attr("href").trim()
                    if (href.isNotBlank() && seen.add(href)) links.add(href)
                }
            }
        }
        return links
    }

    private suspend fun resolveFourKLink(raw: String, hubcloud: String): List<FourKStream> {
        val resolved = if (raw.contains("id=")) {
            runCatching { getFourKRedirectLinks(raw) }.getOrNull().orEmpty()
        } else raw
        if (resolved.isBlank()) return emptyList()
        return dispatchFourKHost(resolved, hubcloud)
    }

    private suspend fun getFourKRedirectLinks(url: String): String {
        val html = runCatching { getText(url) }.getOrNull() ?: return ""
        val regex = Regex("""s\('o','([A-Za-z0-9+/=]+)'|ck\('_wp_http_\d+','([^']+)'""")
        val combined = StringBuilder()
        for (m in regex.findAll(html)) {
            (m.groups[1]?.value ?: m.groups[2]?.value)?.let { combined.append(it) }
        }
        if (combined.isEmpty()) return ""
        return runCatching {
            val decoded = b64d(rot13(b64d(b64d(combined.toString()))))
            val json = gson.fromJson(decoded, JsonObject::class.java)
            val encodedUrl = b64d(json.string("o") ?: "").trim()
            if (encodedUrl.isNotBlank()) return@runCatching encodedUrl
            val data = b64d(json.string("data") ?: "")
            val wp = json.string("blog_url") ?: ""
            if (wp.isBlank() || data.isBlank()) return@runCatching ""
            val t = runCatching { getText("$wp?re=$data") }.getOrNull().orEmpty()
            org.jsoup.Jsoup.parse(t).text().trim()
        }.getOrDefault("")
    }

    private suspend fun dispatchFourKHost(url: String, hubcloud: String): List<FourKStream> {
        val lower = url.lowercase(Locale.US)
        return runCatching {
            when {
                "hubcloud" in lower -> extractHubCloud(url, hubcloud)
                "hubdrive" in lower -> extractHubDrive(url, hubcloud)
                "hblinks" in lower -> extractHblinks(url, hubcloud)
                "hubcdn" in lower -> extractHubCdn(url)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun getFourKQuality(str: String): String {
        val m = Regex("(\\d{3,4})[pP]").find(str)?.groupValues?.get(1)
        return if (m != null) "${m}p" else "2160p"
    }

    // Parse a release filename/header into a compact spec for the picker.
    private fun parseFourKRelease(name: String): FourKRelease {
        val tags = mutableListOf<String>()
        val resM = Regex("(\\d{3,4})[pP]").find(name)?.groupValues?.get(1)
        val res = resM?.let { "${it}p" }
            ?: if (Regex("\\b(4k|uhd|2160)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) "2160p" else ""
        if (res.isNotEmpty()) tags.add(res)

        val sourceTags = listOf(
            Regex("\\bremux\\b", RegexOption.IGNORE_CASE) to "REMUX",
            Regex("blu[\\s._-]?ray|\\bbdrip\\b", RegexOption.IGNORE_CASE) to "BluRay",
            Regex("web[\\s._-]?dl", RegexOption.IGNORE_CASE) to "WEB-DL",
            Regex("web[\\s._-]?rip", RegexOption.IGNORE_CASE) to "WEBRip",
            Regex("\\bhdrip\\b", RegexOption.IGNORE_CASE) to "HDRip",
            Regex("\\bhdtv\\b", RegexOption.IGNORE_CASE) to "HDTV",
            Regex("\\bdvdrip\\b", RegexOption.IGNORE_CASE) to "DVDRip",
            Regex("\\bcam\\b|hdcam", RegexOption.IGNORE_CASE) to "CAM",
        ).filter { it.first.containsMatchIn(name) }.map { it.second }
            .let { if ("REMUX" in it) it.filter { t -> t != "BluRay" } else it } // REMUX implies BluRay
        tags.addAll(sourceTags)

        if (Regex("dolby[\\s._-]?vision|\\bdv\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) tags.add("DV")
        if (Regex("hdr10\\+", RegexOption.IGNORE_CASE).containsMatchIn(name)) tags.add("HDR10+")
        else if (Regex("\\bhdr\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) tags.add("HDR")

        val codec = listOf(
            Regex("hevc|x265|h[\\s._-]?265", RegexOption.IGNORE_CASE) to "HEVC",
            Regex("x264|h[\\s._-]?264|\\bavc\\b", RegexOption.IGNORE_CASE) to "x264",
            Regex("\\bav1\\b", RegexOption.IGNORE_CASE) to "AV1",
        ).firstOrNull { it.first.containsMatchIn(name) }?.second
        if (codec != null) tags.add(codec)

        val audio = mutableListOf<String>()
        if (Regex("atmos", RegexOption.IGNORE_CASE).containsMatchIn(name)) audio.add("Atmos")
        if (Regex("true[\\s._-]?hd", RegexOption.IGNORE_CASE).containsMatchIn(name)) audio.add("TrueHD")
        if (Regex("\\bdts(-?hd)?\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) audio.add("DTS")
        if (Regex("ddp|dd\\+|e-?ac-?3", RegexOption.IGNORE_CASE).containsMatchIn(name)) audio.add("DDP")
        else if (Regex("\\bdd\\b|\\bac-?3\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) audio.add("DD")
        if (Regex("\\baac\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) audio.add("AAC")

        val langDefs = listOf(
            Regex("hindi|\\bhin\\b", RegexOption.IGNORE_CASE) to "Hin",
            Regex("english|\\beng\\b", RegexOption.IGNORE_CASE) to "Eng",
            Regex("tamil|\\btam\\b", RegexOption.IGNORE_CASE) to "Tam",
            Regex("telugu|\\btel\\b", RegexOption.IGNORE_CASE) to "Tel",
            Regex("malayalam|\\bmal\\b", RegexOption.IGNORE_CASE) to "Mal",
            Regex("kannada|\\bkan\\b", RegexOption.IGNORE_CASE) to "Kan",
            Regex("japanese|\\bjpn\\b", RegexOption.IGNORE_CASE) to "Jpn",
            Regex("korean|\\bkor\\b", RegexOption.IGNORE_CASE) to "Kor",
        )
        val langs = langDefs.filter { it.first.containsMatchIn(name) }.map { it.second }
        val langStr = langs.joinToString("+")
        return FourKRelease(tags.joinToString(" "), audio.joinToString("/"), langStr, res.ifEmpty { "2160p" })
    }

    private fun parseSizeGb(size: String): Double {
        val m = Regex("([\\d.]+)\\s*(GB|MB)", RegexOption.IGNORE_CASE).find(size) ?: return 0.0
        val v = m.groupValues[1].toDoubleOrNull() ?: return 0.0
        return if (m.groupValues[2].equals("MB", ignoreCase = true)) v / 1024.0 else v
    }

    private fun fourKQualityRank(q: String): Int {
        val t = q.lowercase(Locale.US)
        return when {
            "2160" in t || "4k" in t -> 4
            "1440" in t -> 3
            "1080" in t -> 2
            "720" in t -> 1
            else -> 0
        }
    }

    // Concise title: spec (resolution/source/range/codec) + languages + size. The verbose
    // audio-codec list and server suffix are dropped to stop names overflowing/being trimmed.
    private fun buildFourKTitle(s: FourKStream, mediaType: String, season: Int?, episode: Int?): String {
        val parts = mutableListOf<String>()
        if (s.spec.isNotBlank()) parts.add(s.spec)
        if (s.langs.isNotBlank()) parts.add(s.langs)
        if (s.size.isNotBlank()) parts.add(s.size)
        var line = parts.joinToString(" · ")
        if (mediaType == "tv") {
            val tag = "S${(season ?: 1).toString().padStart(2, '0')}E${(episode ?: 1).toString().padStart(2, '0')}"
            line = "$tag · $line"
        }
        return line.ifBlank { s.server.ifBlank { "4KHDHub" } }
    }

    private suspend fun extractHubCloud(url: String, hubcloudDomain: String): List<FourKStream> {
        val out = mutableListOf<FourKStream>()
        val baseUrl = runCatching { java.net.URI(url).let { "${it.scheme}://${it.host}" } }.getOrNull()
            ?: hubcloudDomain.trimEnd('/')
        val href = if (url.contains("hubcloud.php")) {
            url
        } else {
            val html = runCatching { getText(url) }.getOrNull() ?: return out
            val raw = org.jsoup.Jsoup.parse(html).selectFirst("#download")?.attr("href")?.trim().orEmpty()
            if (raw.isBlank()) return out
            if (raw.startsWith("http")) raw else "${baseUrl.trimEnd('/')}/${raw.trimStart('/')}"
        }
        if (href.isBlank()) return out

        val pageHtml = runCatching { getText(href) }.getOrNull() ?: return out
        val doc = org.jsoup.Jsoup.parse(pageHtml)
        val size = doc.selectFirst("#size")?.text()?.trim().orEmpty()
        val header = doc.selectFirst("div.card-header")?.text()?.trim().orEmpty()
        val rel = parseFourKRelease(header)
        fun stream(url: String, server: String) =
            FourKStream(url, rel.quality, server, rel.spec, rel.audio, rel.langs, size)

        doc.select("a.btn").forEach { el ->
            val link = el.attr("href").trim()
            val label = el.text().lowercase(Locale.US)
            if (link.isBlank()) return@forEach
            when {
                // "Download File" → dead/throttled workers.dev direct file; does not stream. Skip.
                "fsl server" in label -> out.add(stream(link, "FSL"))
                "s3 server" in label -> out.add(stream(link, "S3"))
                "pixeldra" in label || "pixel server" in label || "pixeldrain" in label -> {
                    val base = runCatching { java.net.URI(link).let { "${it.scheme}://${it.host}" } }.getOrDefault("")
                    val finalUrl = if ("download" in link) link
                    else "$base/api/file/${link.substringAfterLast("/")}?download"
                    out.add(stream(finalUrl, "Pixeldrain"))
                }
                "buzzserver" in label -> {
                    val dlink = runCatching { fourKHeaderRedirect("$link/download", link) }.getOrNull().orEmpty()
                    if (dlink.isNotBlank()) out.add(stream(dlink, "BuzzServer"))
                }
                "10gbps" in label || "mega" in label || "pdl" in label || "fslv2" in label ->
                    out.add(stream(link, "10Gbps"))
            }
        }
        return out
    }

    private suspend fun fourKHeaderRedirect(url: String, referer: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(okhttp3.Headers.headersOf("User-Agent", USER_AGENT, "Referer", referer))
            .get()
            .build()
        noRedirectClient.newCall(request).execute().use { r ->
            r.header("hx-redirect") ?: r.header("HX-Redirect") ?: ""
        }
    }

    private suspend fun extractHubDrive(url: String, hubcloud: String): List<FourKStream> {
        val html = runCatching { getText(url) }.getOrNull() ?: return emptyList()
        val doc = org.jsoup.Jsoup.parse(html)
        var href = doc.selectFirst(".btn.btn-primary.btn-user.btn-success1.m-1")?.attr("href").orEmpty()
        if (href.isBlank()) {
            href = doc.select("a").firstOrNull { it.attr("href").contains("hubcloud", ignoreCase = true) }
                ?.attr("href").orEmpty()
        }
        if (href.isBlank()) return emptyList()
        return if (href.contains("hubcloud", ignoreCase = true)) extractHubCloud(href, hubcloud) else emptyList()
    }

    private suspend fun extractHblinks(url: String, hubcloud: String): List<FourKStream> {
        val html = runCatching { getText(url) }.getOrNull() ?: return emptyList()
        val doc = org.jsoup.Jsoup.parse(html)
        val hrefs = doc.select("h3 a, h5 a, div.entry-content p a")
            .mapNotNull { it.attr("href").trim().ifBlank { null } }
        val out = mutableListOf<FourKStream>()
        hrefs.forEach { h -> out.addAll(dispatchFourKHost(h, hubcloud)) }
        return out
    }

    private suspend fun extractHubCdn(url: String): List<FourKStream> {
        val html = runCatching { getText(url) }.getOrNull() ?: return emptyList()
        val m = Regex("""reurl\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1) ?: return emptyList()
        val after = m.substringAfter("?r=", "").ifBlank { m.substringAfter("link=", "") }
        if (after.isBlank()) return emptyList()
        val decoded = b64d(after)
        val link = if (decoded.contains("link=")) decoded.substringAfterLast("link=") else decoded
        return if (link.isNotBlank()) listOf(FourKStream(link, "Auto", "HubCdn")) else emptyList()
    }

    // ── End 4KHDHub ──────────────────────────────────────────────────────────────

    // ── AniDB ────────────────────────────────────────────────────────────────────
    // NOTE: anidb.app is behind a Cloudflare TLS-fingerprint (JA3) managed challenge,
    // NOT a JS challenge — it passes/fails on the TLS handshake alone (no cf_clearance
    // cookie needed). Android's OkHttp uses the system BoringSSL stack, whose fingerprint
    // matches Chrome's, so this resolves on-device just like the CloudStream addon does.
    // Desktop/JVM/Node TLS stacks get HTTP 403 ("Just a moment..."), so this returns empty
    // in unit/CI tests run off-device.

    private suspend fun resolveAniDb(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val base = "https://anidb.app"
        val ranked = rankAniDb(searchAniDb(base, details.title), details.title)
        val targetEpisode = if (mediaType == "tv") (episode ?: 1) else 1

        for (candidate in ranked.take(3)) {
            val slug = candidate.url.trimEnd('/').substringAfterLast("/")
            val siteId = slug.substringAfterLast("-").toIntOrNull() ?: continue

            val episodes = getJson(
                "$base/api/frontend/anime/$siteId/episodes",
                mapOf("X-Requested-With" to "XMLHttpRequest")
            )?.getArray("episodes")?.toList().orEmpty().mapNotNull { it.asJsonObjectOrNull() }
            if (episodes.isEmpty()) continue

            val target = episodes.firstOrNull {
                runCatching { it.get("number")?.asInt }.getOrNull() == targetEpisode
            } ?: episodes.getOrNull(targetEpisode - 1) ?: episodes.firstOrNull() ?: continue
            val epId = target.get("id")?.asStringOrNull() ?: continue

            val languages = getJson(
                "$base/api/frontend/episode/$epId/languages",
                mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to "$base/anime/$slug")
            )?.getArray("languages")?.toList().orEmpty().mapNotNull { it.asJsonObjectOrNull() }

            val embedUrls = languages.mapNotNull { l ->
                val eu = l.string("embed_url") ?: return@mapNotNull null
                eu to (l.string("name") ?: l.string("code") ?: "")
            }
            if (embedUrls.isEmpty()) continue

            val streams = coroutineScope {
                embedUrls.map { (eu, name) ->
                    async(Dispatchers.IO) {
                        val m3u8 = runCatching { extractAniDbEmbed(eu, base) }.getOrNull() ?: return@async null
                        val langLabel = if (name.isNotBlank()) " [$name]" else ""
                        val label = if (mediaType == "tv") {
                            "${details.title} E${targetEpisode.toString().padStart(2, '0')}$langLabel · AniDB"
                        } else {
                            "${details.title}$langLabel · AniDB"
                        }
                        HttpResolvedStream("AniDB", label, m3u8, "Auto", mapOf("Referer" to "$base/"))
                    }
                }.awaitAll().filterNotNull()
            }.distinctBy { it.url }

            if (streams.isNotEmpty()) return streams
        }
        return emptyList()
    }

    private suspend fun searchAniDb(base: String, query: String): List<AniDbResult> {
        val html = runCatching {
            getText(
                "$base/browse?q=${query.urlEncode()}",
                mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        }.getOrNull() ?: return emptyList()
        val doc = org.jsoup.Jsoup.parse(html)
        val seen = mutableSetOf<String>()
        val results = mutableListOf<AniDbResult>()
        doc.select("a.anime-card").forEach { a ->
            val href = absoluteUrl(base, a.attr("href"))
            val title = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }.trim()
            if (href.isNotBlank() && title.isNotBlank() && seen.add(href)) results.add(AniDbResult(href, title))
        }
        return results
    }

    private fun rankAniDb(results: List<AniDbResult>, title: String): List<AniDbResult> {
        val want = normTitle(title)
        val exact = results.filter { normTitle(it.title) == want }
        val partial = results.filter {
            val n = normTitle(it.title)
            n != want && (n.contains(want) || want.contains(n))
        }
        return exact + partial
    }

    private suspend fun extractAniDbEmbed(embedUrl: String, base: String): String? {
        val text = runCatching { getText(embedUrl, mapOf("Referer" to "$base/")) }.getOrNull() ?: return null
        val regexes = listOf(
            Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""sources\s*:\s*\[\s*\{[^}]*file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        )
        for (r in regexes) {
            r.find(text)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    // ── End AniDB ────────────────────────────────────────────────────────────────

    // ── HDGharTV ────────────────────────────────────────────────────────────────
    
    private suspend fun resolveHdGharTv(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val isTv = mediaType == "tv" || mediaType == "series"
        val tmdbType = if (isTv) "tv" else "movie"
        
        // 1. Get Title from TMDB for Search
        val tmdbUrl = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=${Constants.TMDB_API_KEY}"
        val tmdbJson = runCatching { getText(tmdbUrl) }.getOrNull() ?: return emptyList()
        val tmdbData = runCatching { org.json.JSONObject(tmdbJson) }.getOrNull() ?: return emptyList()
        val titleName = tmdbData.optString("name", tmdbData.optString("title", fallbackTitle))
        if (titleName.isEmpty()) return emptyList()
        
        val releaseYear = tmdbData.optString("release_date", tmdbData.optString("first_air_date", fallbackYear?.toString() ?: "N/A")).split("-").firstOrNull() ?: "N/A"
        val displayTitle = if (isTv) "📺 $titleName - ($releaseYear) S${season ?: 1}E${episode ?: 1}" else "🎦 $titleName ($releaseYear)"
        
        // 2. Search hdghartv.cc
        val hdGharApi = "https://hdghartv.cc/api"
        val searchUrl = "$hdGharApi/search?q=${java.net.URLEncoder.encode(titleName, "UTF-8")}&type=all&page=1"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "https://hdghartv.cc/"
        )
        val searchJson = runCatching { getText(searchUrl, headers) }.getOrNull() ?: return emptyList()
        val searchData = runCatching { org.json.JSONObject(searchJson) }.getOrNull() ?: return emptyList()
        
        var targetId: String? = null
        
        val primaryArray = searchData.optJSONArray(if (isTv) "series" else "movies")
        if (primaryArray != null) {
            for (i in 0 until primaryArray.length()) {
                val item = primaryArray.optJSONObject(i) ?: continue
                if (item.optInt("tmdbId") == tmdbId) {
                    targetId = item.optString("_id")
                    break
                }
            }
        }
        
        if (targetId == null) {
            val secondaryArray = searchData.optJSONArray(if (isTv) "movies" else "series")
            if (secondaryArray != null) {
                for (i in 0 until secondaryArray.length()) {
                    val item = secondaryArray.optJSONObject(i) ?: continue
                    if (item.optInt("tmdbId") == tmdbId) {
                        targetId = item.optString("_id")
                        break
                    }
                }
            }
        }
        
        if (targetId.isNullOrEmpty()) return emptyList()
        val apiType = if (isTv) "series" else "movies"
        val detailsUrl = "$hdGharApi/$apiType/public/$targetId"
        val detailsJson = runCatching { getText(detailsUrl, headers) }.getOrNull() ?: return emptyList()
        val detailsData = runCatching { org.json.JSONObject(detailsJson) }.getOrNull() ?: return emptyList()
        
        var streamingLinks: org.json.JSONArray? = null
        if (!isTv) {
            streamingLinks = detailsData.optJSONArray("streamingLinks")
        } else {
            val seasons = detailsData.optJSONArray("seasons")
            if (seasons != null) {
                for (i in 0 until seasons.length()) {
                    val s = seasons.optJSONObject(i) ?: continue
                    if (s.optInt("seasonNumber") == (season ?: 1)) {
                        val eps = s.optJSONArray("episodes")
                        if (eps != null) {
                            for (j in 0 until eps.length()) {
                                val ep = eps.optJSONObject(j) ?: continue
                                if (ep.optInt("episodeNumber") == (episode ?: 1)) {
                                    streamingLinks = ep.optJSONArray("streamingLinks")
                                    break
                                }
                            }
                        }
                        break
                    }
                }
            }
        }
        
        if (streamingLinks == null || streamingLinks.length() == 0) return emptyList()
        
        val results = mutableListOf<HttpResolvedStream>()
        for (i in 0 until streamingLinks.length()) {
            val linkObj = streamingLinks.optJSONObject(i) ?: continue
            val url = linkObj.optString("url")
            val quality = linkObj.optString("quality", "Auto")
            
            if (url.isNotEmpty()) {
                val icon = if (quality.contains("2160") || quality.contains("4K") || quality.contains("4k")) "💎" else if (quality.contains("1080")) "🔥" else "🎬"
                val audio = "Dual-Audio 🌐"
                val format = if (url.contains(".m3u8")) "HLS" else "MP4"
                
                val desc = "⚡ $format\n$icon $quality | 🔊 $audio\n🛰️ Source: HDGharTV"
                
                results.add(
                    HttpResolvedStream(
                        provider = "HDGharTV",
                        title = displayTitle,
                        url = url,
                        quality = quality,
                        description = desc
                    )
                )
            }
        }
        return results
    }

    // ── Castle ────────────────────────────────────────────────────────────────
    
    private suspend fun resolveCastle(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val castleBase = "https://api.hlowb.com"
        val channel = "IndiaA"
        val clientType = "1"
        val lang = "en-US"
        val pkg = "com.external.castle"

        fun decryptCastle(ciphertext: String, securityKey: String): String {
            val decodedSecurityKey = android.util.Base64.decode(securityKey, android.util.Base64.DEFAULT)
            val suffixBytes = "T!BgJB".toByteArray(Charsets.UTF_8)
            var keyBytes = decodedSecurityKey + suffixBytes
            
            if (keyBytes.size < 16) {
                val padded = ByteArray(16)
                System.arraycopy(keyBytes, 0, padded, 0, keyBytes.size)
                keyBytes = padded
            } else if (keyBytes.size > 16) {
                keyBytes = keyBytes.copyOfRange(0, 16)
            }
            
            var cipherClean = ciphertext.trim()
            try {
                val json = org.json.JSONObject(cipherClean)
                if (json.has("data") && json.opt("data") is String) {
                    cipherClean = json.getString("data").trim()
                }
            } catch (e: Exception) {}
            
            val decodedCipher = android.util.Base64.decode(cipherClean, android.util.Base64.DEFAULT)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val ivSpec = javax.crypto.spec.IvParameterSpec(keyBytes)
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec)
            return String(cipher.doFinal(decodedCipher), Charsets.UTF_8)
        }

        val apiHeaders = mapOf(
            "User-Agent" to "okhttp/4.9.3",
            "Accept" to "application/json",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "Keep-Alive",
            "Referer" to castleBase
        )
        
        suspend fun postCastle(url: String, json: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val req = okhttp3.Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "okhttp/4.9.3")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Connection", "Keep-Alive")
                .header("Referer", castleBase)
                .build()
            okHttpClient.newCall(req).execute().body?.string() ?: ""
        }

        val tmdbType = if (mediaType == "tv") "tv" else "movie"
        val tmdbUrl = "https://api.themoviedb.org/3/$tmdbType/$tmdbId?api_key=${Constants.TMDB_API_KEY}"
        val tmdbJson = runCatching { getText(tmdbUrl) }.getOrNull() ?: return emptyList()
        val tmdbData = runCatching { org.json.JSONObject(tmdbJson) }.getOrNull() ?: return emptyList()
        val title = tmdbData.optString("name", tmdbData.optString("title", fallbackTitle))
        if (title.isEmpty()) return emptyList()
        val releaseYear = tmdbData.optString("release_date", tmdbData.optString("first_air_date", fallbackYear?.toString() ?: "N/A")).split("-").firstOrNull() ?: "N/A"
        
        val secUrl = "$castleBase/v0.1/system/getSecurityKey/1?channel=$channel&clientType=$clientType&lang=$lang"
        val secJson = runCatching { getText(secUrl, apiHeaders) }.getOrNull() ?: return emptyList()
        val securityKey = org.json.JSONObject(secJson).optString("data")
        if (securityKey.isEmpty()) return emptyList()

        val searchKeyword = if (mediaType != "tv" && releaseYear != "N/A") "$title $releaseYear" else title
        val searchUrl = "$castleBase/film-api/v1.1.0/movie/searchByKeyword?channel=$channel&clientType=$clientType&keyword=${java.net.URLEncoder.encode(searchKeyword, "UTF-8")}&lang=$lang&mode=1&packageName=$pkg&page=1&size=30"
        val searchEnc = runCatching { getText(searchUrl, apiHeaders) }.getOrNull() ?: return emptyList()
        val searchDec = runCatching { decryptCastle(searchEnc, securityKey) }.getOrNull() ?: return emptyList()
        val searchData = org.json.JSONObject(searchDec).optJSONObject("data") ?: return emptyList()
        val rows = searchData.optJSONArray("rows") ?: return emptyList()
        
        var movieId: String? = null
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            val rTitle = r.optString("title", r.optString("languageName", "")).lowercase()
            if (rTitle.contains(title.lowercase()) || title.lowercase().contains(rTitle)) {
                movieId = r.optString("id", r.optString("redirectIdStr", ""))
                if (movieId.isNotEmpty()) break
            }
        }
        if (movieId.isNullOrEmpty()) {
            movieId = rows.optJSONObject(0)?.optString("id", rows.optJSONObject(0)?.optString("redirectIdStr", ""))
            if (movieId.isNullOrEmpty()) return emptyList()
        }

        suspend fun getCastleDetails(mId: String): org.json.JSONObject? {
            val dUrl = "$castleBase/film-api/v1.9.9/movie?channel=$channel&clientType=$clientType&lang=$lang&movieId=$mId&packageName=$pkg"
            val dEnc = runCatching { getText(dUrl, apiHeaders) }.getOrNull() ?: return null
            val dDec = runCatching { decryptCastle(dEnc, securityKey) }.getOrNull() ?: return null
            return org.json.JSONObject(dDec).optJSONObject("data")
        }
        
        var details = getCastleDetails(movieId) ?: return emptyList()
        var currentMovieId = movieId
        
        if (mediaType == "tv" && season != null && episode != null) {
            val seasons = details.optJSONArray("seasons")
            var foundSeason = false
            if (seasons != null) {
                for (i in 0 until seasons.length()) {
                    val s = seasons.optJSONObject(i) ?: continue
                    if (s.optInt("number") == season) {
                        foundSeason = true
                        val rid = s.optString("redirectId")
                        if (rid.isNotEmpty() && rid != currentMovieId) {
                            details = getCastleDetails(rid) ?: details
                            currentMovieId = rid
                        }
                        break
                    }
                }
            }
            if (!foundSeason) {
                if (details.has("seasonNumber") && details.optInt("seasonNumber") != season) {
                    return emptyList()
                }
            }
        }
        
        var episodeId: String? = null
        val episodes = details.optJSONArray("episodes") ?: org.json.JSONArray()
        val targetEp = if (mediaType == "tv") {
            for (i in 0 until episodes.length()) {
                val e = episodes.optJSONObject(i) ?: continue
                if (e.optInt("number") == episode) {
                    episodeId = e.optString("id")
                    break
                }
            }
            if (episodeId.isNullOrEmpty()) return emptyList()
            var found: org.json.JSONObject? = null
            for (i in 0 until episodes.length()) {
                val e = episodes.optJSONObject(i) ?: continue
                if (e.optString("id") == episodeId) {
                    found = e
                    break
                }
            }
            found
        } else {
            if (episodes.length() > 0) episodeId = episodes.optJSONObject(0)?.optString("id")
            details
        }
        val tracks = targetEp?.optJSONArray("tracks") ?: org.json.JSONArray()
        
        val displayTitle = if (mediaType == "tv") "📺 $title - ($releaseYear) S${season ?: 1}E${episode ?: 1}" else "🎦 $title ($releaseYear)"
        val results = mutableListOf<HttpResolvedStream>()
        
        fun parseVideo(vDec: String, langLabel: String, reqResolution: String) {
            val vData = org.json.JSONObject(vDec).optJSONObject("data") ?: return
            
            val videos = vData.optJSONArray("videos")
            var added = false
            if (videos != null && videos.length() > 0) {
                for (i in 0 until videos.length()) {
                    val vObj = videos.optJSONObject(i) ?: continue
                    val url = vObj.optString("url")
                    if (url.isNotEmpty()) {
                        val qual = vObj.optString("resolutionDescription", vObj.optString("resolution", "720p")).replace(Regex("^(SD|HD|FHD)\\s+", RegexOption.IGNORE_CASE), "")
                        val desc = "Castle $langLabel - $qual"
                        results.add(HttpResolvedStream(provider = "castle", title = displayTitle, url = url, quality = qual, description = desc))
                        added = true
                    } else if (vObj.optString("resolution") == reqResolution || vObj.optInt("resolution").toString() == reqResolution) {
                        val rootUrl = vData.optString("videoUrl")
                        if (rootUrl.isNotEmpty()) {
                            val qual = vObj.optString("resolutionDescription", vObj.optString("resolution", "720p")).replace(Regex("^(SD|HD|FHD)\\s+", RegexOption.IGNORE_CASE), "")
                            val desc = "Castle $langLabel - $qual"
                            results.add(HttpResolvedStream(provider = "castle", title = displayTitle, url = rootUrl, quality = qual, description = desc))
                            added = true
                        }
                    }
                }
            }
            if (!added) {
                val url = vData.optString("videoUrl")
                if (url.isNotEmpty()) {
                    val fallbackQual = if (reqResolution == "3") "1080p" else "720p"
                    val desc = "Castle $langLabel - $fallbackQual"
                    results.add(HttpResolvedStream(provider = "castle", title = displayTitle, url = url, quality = fallbackQual, description = desc))
                }
            }
        }
        
        val resolutionsToFetch = listOf("3", "2") // 1080P, 720P
        
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            if (t.optBoolean("existIndividualVideo") && t.has("languageId")) {
                val langId = t.optString("languageId")
                val langName = t.optString("languageName", "Unknown")
                
                for (resolution in resolutionsToFetch) {
                    val v1Url = "$castleBase/film-api/v2.0.1/movie/getVideo2?clientType=$clientType&packageName=$pkg&channel=$channel&lang=$lang"
                    val body = org.json.JSONObject().apply {
                        put("mode", "1")
                        put("appMarket", "GuanWang")
                        put("clientType", clientType)
                        put("woolUser", "false")
                        put("apkSignKey", "ED0955EB04E67A1D9F3305B95454FED485261475")
                        put("androidVersion", "13")
                        put("movieId", currentMovieId)
                        put("episodeId", episodeId)
                        put("languageId", langId)
                        put("isNewUser", "false")
                        put("resolution", resolution)
                        put("packageName", pkg)
                    }
                    val v1Enc = runCatching { postCastle(v1Url, body.toString()) }.getOrNull() ?: continue
                    val v1Dec = runCatching { decryptCastle(v1Enc, securityKey) }.getOrNull() ?: continue
                    parseVideo(v1Dec, "[$langName]", resolution)
                }
            }
        }
        
        if (results.isEmpty()) {
            for (resolution in resolutionsToFetch) {
                val v2Url = "$castleBase/film-api/v2.0.1/movie/getVideo2?clientType=$clientType&packageName=$pkg&channel=$channel&lang=$lang"
                val body = org.json.JSONObject().apply {
                    put("mode", "1")
                    put("appMarket", "GuanWang")
                    put("clientType", clientType)
                    put("woolUser", "false")
                    put("apkSignKey", "ED0955EB04E67A1D9F3305B95454FED485261475")
                    put("androidVersion", "13")
                    put("movieId", currentMovieId)
                    put("episodeId", episodeId)
                    put("isNewUser", "false")
                    put("resolution", resolution)
                    put("packageName", pkg)
                }
                val v2Enc = runCatching { postCastle(v2Url, body.toString()) }.getOrNull()
                if (v2Enc != null) {
                    val v2Dec = runCatching { decryptCastle(v2Enc, securityKey) }.getOrNull()
                    if (v2Dec != null) {
                        parseVideo(v2Dec, "[Shared]", resolution)
                    }
                }
            }
        }
        
        return results
    }

    private suspend fun resolveNewTvApiUrl(): String {
        if (newTvApiUrl.isNotEmpty()) return newTvApiUrl
        for (encoded in NEW_TV_DOMAINS) {
            val base = runCatching {
                android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    .toString(Charsets.UTF_8).trimEnd('/')
            }.getOrNull() ?: continue
            runCatching {
                val data = getJson(
                    "$base/checknewtv.php",
                    mapOf("User-Agent" to USER_AGENT, "X-Requested-With" to "NetmirrorNewTV v1.0")
                ) ?: return@runCatching
                val tokenHash = data.string("token_hash") ?: return@runCatching
                val resolved = android.util.Base64.decode(tokenHash, android.util.Base64.DEFAULT)
                    .toString(Charsets.UTF_8).trimEnd('/')
                newTvApiUrl = resolved
                return resolved
            }
        }
        error("Failed to resolve NetMirror API URL")
    }

    private fun buildNewTvHeaders(ott: String, extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache",
            "Expires" to "0",
            "X-Requested-With" to "NetmirrorNewTV v1.0",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
            "Accept" to "application/json, text/plain, */*",
            "Ott" to ott
        ) + extra

    private suspend fun resolveVidSrc(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): List<HttpResolvedStream> = runCatching {
        val meta = fetchTmdbDetails(tmdbId, mediaType, "", null)
        val imdbId = meta.imdbId ?: return@runCatching emptyList<HttpResolvedStream>()
        val embedUrl = if (mediaType == "tv") {
            "https://vsrc.su/embed/tv?imdb=$imdbId&season=${season ?: 1}&episode=${episode ?: 1}"
        } else {
            "https://vsrc.su/embed/$imdbId"
        }
        val embedHtml = getText(embedUrl)
        val iframeSrc = IFRAME_SRC_REGEX.find(embedHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return@runCatching emptyList<HttpResolvedStream>()
        val iframeUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
        val iframeHtml = getText(iframeUrl, mapOf("Referer" to "https://vsrc.su/"))
        val prorcpSrc = PRORCP_SRC_REGEX.find(iframeHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return@runCatching emptyList<HttpResolvedStream>()
        val cloudUrl = URL(URL("https://cloudnestra.com/"), prorcpSrc).toString()
        val cloudHtml = getText(cloudUrl, mapOf("Referer" to "https://cloudnestra.com/"))
        val divMatch = DIV_MATCH_REGEX.find(cloudHtml) ?: return@runCatching emptyList<HttpResolvedStream>()
        val decrypted = postJson(
            url = "https://enc-dec.app/api/dec-cloudnestra",
            body = """{"text":${gson.toJson(divMatch.groupValues[2])},"div_id":${gson.toJson(divMatch.groupValues[1])}}"""
        )
        (decrypted?.getArray("result")?.toList().orEmpty()).mapIndexedNotNull { index: Int, element: JsonElement ->
            val streamUrl = element.asStringOrNull() ?: return@mapIndexedNotNull null
            HttpResolvedStream(
                provider = "VidSrc",
                title = "VidSrc Server ${index + 1}",
                url = streamUrl,
                quality = "Auto",
                headers = mapOf(
                    "Referer" to "https://cloudnestra.com/",
                    "Origin" to "https://cloudnestra.com"
                )
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun decryptCloudnestraStreams(
        provider: String,
        title: String,
        sourceUrl: String,
        referer: String
    ): List<HttpResolvedStream> {
        val cloudHtml = getText(sourceUrl, mapOf("Referer" to referer))
        val prorcpSrc = PRORCP_SRC_REGEX.find(cloudHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        val cloudUrl = URL(URL(sourceUrl), prorcpSrc).toString()
        val finalHtml = getText(cloudUrl, mapOf("Referer" to sourceUrl))
        val hidden = DIV_MATCH_REGEX.find(finalHtml) ?: return emptyList()
        val decrypted = postJson(
            url = "https://enc-dec.app/api/dec-cloudnestra",
            body = """{"text":${gson.toJson(hidden.groupValues[2])},"div_id":${gson.toJson(hidden.groupValues[1])}}"""
        )
        val urls = decrypted?.getArray("result")?.toList().orEmpty()
            .mapNotNull { it.asStringOrNull() }
            .distinct()
        return urls.mapIndexed { index, streamUrl ->
            HttpResolvedStream(
                provider = provider,
                title = "$provider Server ${index + 1}",
                url = streamUrl,
                quality = qualityFromText(streamUrl),
                headers = mapOf("Referer" to "https://cloudnestra.com/")
            )
        }
    }


    private suspend fun fetchNewTvPlatform(
        platform: NetMirrorPlatform,
        title: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        apiBase: String
    ): List<HttpResolvedStream> {
        val searchHeaders = buildNewTvHeaders(platform.ott)
        val firstResult = getJson("$apiBase${platform.search}?s=${title.urlEncode()}", searchHeaders)
            ?.getArray("searchResult")
            ?.firstOrNull()
            ?.asJsonObjectOrNull()
            ?: return emptyList()
        val contentId = firstResult.string("id") ?: return emptyList()
        val postData = getJson(
            "$apiBase${platform.post}?id=$contentId",
            buildNewTvHeaders(platform.ott, mapOf("Lastep" to "", "Usertoken" to ""))
        ) ?: return emptyList()
        val targetId: String = if (mediaType == "tv") {
            val allEpisodes = getAllNewTvEpisodes(postData, platform, apiBase)
            allEpisodes.firstOrNull { it.season == (season ?: 1) && it.episode == (episode ?: 1) }
                ?.id ?: return emptyList()
        } else {
            postData.string("main_id") ?: contentId
        }
        val playerData = getJson(
            "$apiBase${platform.playlist}?id=$targetId",
            buildNewTvHeaders(platform.ott, mapOf("Usertoken" to ""))
        ) ?: return emptyList()
        val videoLink = playerData.string("video_link")
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: return emptyList()
        val streamTitle = if (mediaType == "tv") {
            val s = (season ?: 1).toString().padStart(2, '0')
            val e = (episode ?: 1).toString().padStart(2, '0')
            "$title S${s}E${e} · NetMirror ${platform.name}"
        } else {
            "$title · NetMirror ${platform.name}"
        }
        return listOf(
            HttpResolvedStream(
                provider = "NetMirror ${platform.name}",
                title = streamTitle,
                url = videoLink,
                quality = "Auto",
                headers = mapOf("Referer" to (playerData.string("referer") ?: apiBase))
            )
        )
    }

    private suspend fun getAllNewTvEpisodes(
        postData: JsonObject,
        platform: NetMirrorPlatform,
        apiBase: String
    ): List<NetMirrorEpisode> {
        val episodes = mutableListOf<NetMirrorEpisode>()
        val seasonList = postData.getArray("season")?.toList().orEmpty().mapNotNull { it.asJsonObjectOrNull() }
        val selectedIdx = seasonList.indexOfFirst { it.get("selected")?.asBoolean == true }.takeIf { it >= 0 } ?: 0
        val selectedSeasonId = seasonList.getOrNull(selectedIdx)?.string("id") ?: postData.string("nextPageSeason")
        val selectedSeasonNumber = selectedIdx + 1

        postData.getArray("episodes")?.toList().orEmpty().mapNotNull { it.asJsonObjectOrNull() }.forEach { ep ->
            val id = ep.string("id") ?: return@forEach
            val epNum = ep.string("ep")?.toIntOrNull()
                ?: ep.string("epNum")?.removePrefix("E")?.toIntOrNull()
                ?: return@forEach
            val sNum = ep.string("sNum")?.removePrefix("S")?.toIntOrNull() ?: selectedSeasonNumber
            episodes.add(NetMirrorEpisode(id, sNum, epNum))
        }

        if (postData.string("nextPageShow") == "1" && selectedSeasonId != null) {
            episodes.addAll(fetchNewTvEpisodesPage(selectedSeasonId, 2, selectedSeasonNumber, platform, apiBase))
        }

        seasonList.forEachIndexed { index, seasonObj ->
            val seasonId = seasonObj.string("id") ?: return@forEachIndexed
            if (seasonId == selectedSeasonId) return@forEachIndexed
            episodes.addAll(fetchNewTvEpisodesPage(seasonId, 1, index + 1, platform, apiBase))
        }

        return episodes
    }

    private suspend fun fetchNewTvEpisodesPage(
        seasonId: String,
        startPage: Int,
        seasonNumber: Int,
        platform: NetMirrorPlatform,
        apiBase: String
    ): List<NetMirrorEpisode> {
        val episodes = mutableListOf<NetMirrorEpisode>()
        var page = startPage
        while (page <= 10) {
            val data = getJson(
                "$apiBase${platform.episodes}?id=$seasonId&page=$page",
                buildNewTvHeaders(platform.ott)
            ) ?: break
            data.getArray("episodes")?.toList().orEmpty().mapNotNull { it.asJsonObjectOrNull() }.forEach { ep ->
                val id = ep.string("id") ?: return@forEach
                val epNum = ep.string("ep")?.toIntOrNull()
                    ?: ep.string("epNum")?.removePrefix("E")?.toIntOrNull()
                    ?: return@forEach
                val sNum = ep.string("sNum")?.removePrefix("S")?.toIntOrNull() ?: seasonNumber
                episodes.add(NetMirrorEpisode(id, sNum, epNum))
            }
            if (data.string("nextPageShow") != "1") break
            page++
        }
        return episodes
    }

    private suspend fun fetchTmdbDetails(
        tmdbId: Int,
        mediaType: String,
        fallbackTitle: String,
        fallbackYear: Int?
    ): HttpScraperTmdbDetails {
        return runCatching {
            val type = if (mediaType == "tv") "tv" else "movie"
            val payload = getJson(
                "https://api.themoviedb.org/3/$type/$tmdbId?api_key=${Constants.TMDB_API_KEY}&append_to_response=external_ids"
            )
            val title = payload?.string(if (type == "tv") "name" else "title")
                ?: fallbackTitle
            val date = payload?.string(if (type == "tv") "first_air_date" else "release_date")
            val year = date?.take(4)?.takeIf { it.all(Char::isDigit) } ?: fallbackYear?.toString()
            val imdbId = payload?.getObject("external_ids")?.string("imdb_id")
                ?: payload?.string("imdb_id")
            HttpScraperTmdbDetails(tmdbId.toString(), title, year, imdbId, type)
        }.getOrElse {
            HttpScraperTmdbDetails(tmdbId.toString(), fallbackTitle, fallbackYear?.toString(), null, mediaType)
        }
    }

    private suspend fun resolveTmdbId(imdbId: String, mediaType: String): Int? {
        val clean = imdbId.trim().takeIf { it.matches(IMDB_ID_REGEX) } ?: return null
        val key = "$mediaType:$clean"
        synchronized(tmdbIdCache) {
            if (tmdbIdCache.containsKey(key)) return tmdbIdCache[key]
        }
        val resolved = runCatching {
            val find = tmdbApi.findByExternalId(clean, Constants.TMDB_API_KEY)
            if (mediaType == "tv") find.tvResults.firstOrNull()?.id else find.movieResults.firstOrNull()?.id
        }.getOrNull()
        synchronized(tmdbIdCache) { tmdbIdCache[key] = resolved }
        return resolved
    }

    private suspend fun fetchManifest(manifestUrl: String): HttpScraperManifest? {
        synchronized(manifestCache) {
            manifestCache[manifestUrl]?.let { return it }
        }
        val parsed = runCatching {
            val json = getText(manifestUrl)
            gson.fromJson(json, HttpScraperManifest::class.java)
        }.getOrNull()?.takeIf { it.name.isNotBlank() && it.scrapers.isNotEmpty() }
        if (parsed != null) {
            synchronized(manifestCache) { manifestCache[manifestUrl] = parsed }
        }
        return parsed
    }

    private suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val requestHeaders = buildMap {
            put("User-Agent", USER_AGENT)
            putAll(headers)
        }
        val request = Request.Builder()
            .url(url)
            .headers(okhttp3.Headers.headersOf(*requestHeaders.flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.string().orEmpty()
        }
    }

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject? {
        return runCatching { gson.fromJson(getText(url, headers), JsonObject::class.java) }.getOrNull()
    }

    private suspend fun getJsonElement(url: String, headers: Map<String, String> = emptyMap()): JsonElement? {
        return runCatching { gson.fromJson(getText(url, headers), JsonElement::class.java) }.getOrNull()
    }

    private suspend fun resolveRedirectUrl(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(okhttp3.Headers.headersOf(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .get()
            .build()
        noRedirectClient.newCall(request).execute().use { response ->
            response.header("Location")
                ?.let { location -> URL(URL(url), location).toString() }
                ?: response.request.url.toString().takeIf { response.isSuccessful }
        }
    }

    private suspend fun postJson(url: String, body: String): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            runCatching { gson.fromJson(response.body?.string().orEmpty(), JsonObject::class.java) }.getOrNull()
        }
    }

    private fun manifestUrlFor(url: String): String {
        val clean = url.trim().substringBefore('#').trimEnd('/')
        githubManifestUrlFor(clean)?.let { return it }
        return if (clean.endsWith("/manifest.json", ignoreCase = true)) clean else "$clean/manifest.json"
    }

    private fun githubManifestUrlFor(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val parts = uri.path.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1]
        return when (host) {
            "github.com", "www.github.com" -> {
                when {
                    parts.size >= 5 && parts[2] == "blob" ->
                        "https://raw.githubusercontent.com/$owner/$repo/${parts[3]}/${parts.drop(4).joinToString("/")}"
                    parts.size >= 4 && parts[2] == "tree" ->
                        "https://raw.githubusercontent.com/$owner/$repo/${parts[3]}/${parts.drop(4).plus("manifest.json").joinToString("/")}"
                    parts.size == 2 || (parts.size == 3 && parts[2].equals("manifest.json", ignoreCase = true)) ->
                        "https://raw.githubusercontent.com/$owner/$repo/main/manifest.json"
                    else -> null
                }
            }
            "raw.githubusercontent.com" -> {
                if (parts.lastOrNull()?.equals("manifest.json", ignoreCase = true) == true) {
                    url
                } else {
                    "$url/manifest.json"
                }
            }
            else -> null
        }
    }

    private fun shortHash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    private fun HttpScraperEntry.isHttpOnlyEnabled(): Boolean {
        if (!enabled) return false
        val normalizedFormats = formats.map { it.lowercase(Locale.US) }.toSet()
        if (normalizedFormats.any { it in P2P_FORMATS }) return false
        return normalizedFormats.isEmpty() || normalizedFormats.any { it in HTTP_FORMATS }
    }

    private fun HttpResolvedStream.toStreamSource(addon: Addon): StreamSource {
        val cleanHeaders = headers
            .mapKeys { it.key.trim() }
            .mapValues { it.value.trim() }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        return StreamSource(
            source = title.ifBlank { provider },
            addonName = "${sanitizeProviderLabel(addon.name)} - $provider",
            addonId = addon.id,
            quality = normalizeQuality(quality),
            size = "",
            sizeBytes = null,
            url = url,
            infoHash = null,
            fileIdx = null,
            behaviorHints = cleanHeaders
                .takeIf { it.isNotEmpty() }
                ?.let { StreamBehaviorHints(proxyHeaders = ProxyHeaders(request = it)) },
            subtitles = emptyList(),
            sources = emptyList(),
            description = description
        )
    }

    private fun normalizeQuality(value: String): String {
        val text = value.lowercase(Locale.US)
        return when {
            "2160" in text || "4k" in text -> "4K"
            "1440" in text -> "1440p"
            "1080" in text -> "1080p"
            "720" in text -> "720p"
            "480" in text -> "480p"
            "360" in text -> "360p"
            else -> "Auto"
        }
    }

    private fun qualityFromText(value: String): String = normalizeQuality(value.ifBlank { "Auto" })

    private fun sanitizeProviderLabel(value: String): String {
        return value.replace(NUVIO_REGEX, "HTTP").trim()
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20")

    private fun JsonObject.string(name: String): String? = get(name)?.asStringOrNull()
    private fun JsonObject.getObject(name: String): JsonObject? = get(name)?.asJsonObjectOrNull()
    private fun JsonObject.getArray(name: String): JsonArray? = get(name)?.asJsonArrayOrNull()
    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonElement.asJsonArrayOrNull(): JsonArray? = if (isJsonArray) asJsonArray else null
    private fun JsonElement.asStringOrNull(): String? = runCatching {
        if (isJsonNull) null else asString
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private data class HttpScraperManifest(
        val name: String = "",
        val version: String = "1.0.0",
        val scrapers: List<HttpScraperEntry> = emptyList()
    )

    private data class HttpScraperEntry(
        val id: String = "",
        val name: String = "",
        val enabled: Boolean = false,
        val formats: List<String> = emptyList(),
        val logo: String? = null
    )

    private data class HttpScraperTmdbDetails(
        val id: String,
        val title: String,
        val year: String?,
        val imdbId: String?,
        val mediaType: String
    )

    private data class HttpResolvedStream(
        val provider: String,
        val title: String,
        val url: String,
        val quality: String,
        val headers: Map<String, String> = emptyMap(),
        val description: String? = null
    )

    private data class VideasyServer(
        val provider: String,
        val name: String,
        val endpoint: String,
        val moviesOnly: Boolean = false
    )

    private data class NetMirrorPlatform(
        val name: String,
        val ott: String,
        val search: String,
        val post: String,
        val episodes: String,
        val playlist: String
    )

    private data class NetMirrorEpisode(val id: String, val season: Int, val episode: Int)

    private data class ToonstreamResult(val url: String, val title: String, val type: String = "movie")
    private data class ToonstreamEpisode(val url: String, val season: Int, val episode: Int)

    private data class FourKResult(val url: String, val title: String)
    private data class FourKStream(
        val url: String,
        val quality: String,
        val server: String,
        val spec: String = "",
        val audio: String = "",
        val langs: String = "",
        val size: String = ""
    )
    private data class FourKPage(val year: Int?, val links: List<String>)
    private data class FourKRelease(val spec: String, val audio: String, val langs: String, val quality: String)
    private data class AniDbResult(val url: String, val title: String)

    companion object {
        private val DIV_EP_REGEX = Regex("""<div[^>]+class=["']ep[^>]*>.*?</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val DATA_IFRAME_REGEX = Regex("""data-iframe=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val IFRAME_PLAYER_REGEX = Regex("""iframe\s+id=["']player_iframe["']\s+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val IFRAME_SRC_REGEX = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val PRORCP_SRC_REGEX = Regex("""src:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        private val DIV_MATCH_REGEX = Regex(
            """<div id="([^"]+)"[^>]*style=["']display\s*:\s*none;?["'][^>]*>([a-zA-Z0-9:/.,{}\-_=+ ]+)</div>""",
            RegexOption.IGNORE_CASE
        )
        private val IMDB_ID_REGEX = Regex("tt\\d{5,}")
        private val NUVIO_REGEX = Regex("nu" + "vio", RegexOption.IGNORE_CASE)
        private const val HTTP_LOCAL_MANIFEST_PREFIX = "http.local."
        private const val LEGACY_LOCAL_MANIFEST_PREFIX = "nu" + "vio.local."
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        private val HTTP_FORMATS = setOf("mp4", "mkv", "m3u8", "hls", "dash")
        private val P2P_FORMATS = setOf("torrent", "magnet", "p2p", "infohash")
        private val NEW_TV_DOMAINS = listOf(
            "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==",
            "aHR0cHM6Ly9tb2JpbGVkZXRlY3QuYXBw",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNsaWNr",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0Lmluaw==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNob3A=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LnN0b3Jl",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0Lndpa2k=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbms=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=",
            "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo="
        )
        private val VIDEASY_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://player.videasy.net",
            "Referer" to "https://player.videasy.net/"
        )
        private val VIDLINK_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json,*/*",
            "Referer" to "https://vidlink.pro/",
            "Origin" to "https://vidlink.pro"
        )
    }
}
