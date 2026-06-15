package com.muvio.shared.repository

import com.muvio.shared.domain.MediaType
import com.muvio.shared.storage.PlatformPreferences
import com.muvio.shared.util.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PlaybackProgress(
    val progressMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
) {
    val percent: Int get() =
        if (durationMs > 0) ((progressMs.toFloat() / durationMs) * 100).toInt().coerceIn(0, 100) else 0
    val isFinished: Boolean get() = percent >= 90
}

class WatchHistoryRepository(
    private val prefs: PlatformPreferences,
    private val profileId: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // In-memory mirror for fast reads (populated from prefs lazily)
    private val _progressMap = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())
    val progressUpdates: Flow<Map<String, PlaybackProgress>> = _progressMap.asStateFlow()

    private fun progressKey(mediaType: MediaType, tmdbId: Int, season: Int?, episode: Int?): String {
        val type = mediaType.name.lowercase()
        return "progress_${profileId}_${type}_${tmdbId}_${season ?: 0}_${episode ?: 0}"
    }

    suspend fun saveProgress(
        mediaType: MediaType,
        tmdbId: Int,
        progressMs: Long,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
    ) {
        val key = progressKey(mediaType, tmdbId, season, episode)
        val entry = PlaybackProgress(
            progressMs = progressMs,
            durationMs = durationMs,
            updatedAt = currentTimeMillis(),
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
        )
        prefs.putString(key, json.encodeToString(entry))
        _progressMap.value = _progressMap.value + (key to entry)
    }

    suspend fun getProgress(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
    ): PlaybackProgress? {
        val key = progressKey(mediaType, tmdbId, season, episode)
        _progressMap.value[key]?.let { return it }
        val raw = prefs.getString(key) ?: return null
        return runCatching { json.decodeFromString<PlaybackProgress>(raw) }
            .onSuccess { _progressMap.value = _progressMap.value + (key to it) }
            .getOrNull()
    }

    suspend fun markWatched(mediaType: MediaType, tmdbId: Int, season: Int? = null, episode: Int? = null) {
        val key = progressKey(mediaType, tmdbId, season, episode)
        val existing = getProgress(mediaType, tmdbId, season, episode)
        val entry = existing?.copy(progressMs = existing.durationMs, updatedAt = currentTimeMillis())
            ?: PlaybackProgress(progressMs = 1, durationMs = 1, updatedAt = currentTimeMillis())
        prefs.putString(key, json.encodeToString(entry))
        _progressMap.value = _progressMap.value + (key to entry)
    }

    suspend fun clearProgress(mediaType: MediaType, tmdbId: Int, season: Int? = null, episode: Int? = null) {
        val key = progressKey(mediaType, tmdbId, season, episode)
        prefs.remove(key)
        _progressMap.value = _progressMap.value - key
    }
}
