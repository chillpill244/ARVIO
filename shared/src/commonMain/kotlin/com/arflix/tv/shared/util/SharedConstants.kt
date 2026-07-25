package com.arflix.tv.shared.util

object SharedConstants {
    var SUPABASE_URL: String = ""
    var SUPABASE_ANON_KEY: String = ""
    var TMDB_API_KEY: String = ""
    var TRAKT_CLIENT_ID: String = ""
    var TRAKT_CLIENT_SECRET: String = ""
    var TRAKT_API_URL: String = "https://api.trakt.tv/"
    const val BACKDROP_BASE_LARGE = "https://image.tmdb.org/t/p/original"
    const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
    const val WATCHED_THRESHOLD = 90
    const val MIN_PROGRESS_THRESHOLD = 3
    const val MAX_PROGRESS_ENTRIES = 50
    const val MAX_CONTINUE_WATCHING = 50
    var TRAKT_PROXY_URL: String = ""
}
