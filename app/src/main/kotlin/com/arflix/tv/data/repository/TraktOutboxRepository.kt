package com.arflix.tv.data.repository
import com.arflix.tv.shared.repository.ProfileManager
import com.arflix.tv.shared.repository.AuthRepository

import com.arflix.tv.shared.util.KmpDateUtils

import android.content.Context
import androidx.annotation.Keep
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val Context.traktOutboxDataStore: DataStore<Preferences> by preferencesDataStore(name = "trakt_outbox")

@Keep
enum class TraktOutboxAction {
    MARK_MOVIE_WATCHED,
    MARK_EPISODE_WATCHED,
    REMOVE_PLAYBACK_ITEM
}

@Keep
data class TraktOutboxItem(
    @OptIn(ExperimentalUuidApi::class)
    val id: String = Uuid.random().toString(),
    val action: TraktOutboxAction,
    val tmdbId: Int? = null,
    val showTraktId: Int? = null,
    val traktEpisodeId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val playbackId: Long? = null,
    val createdAt: String = com.arflix.tv.shared.util.KmpDateUtils.nowIsoString(),
    val attempts: Int = 0
)

class TraktOutboxRepository constructor(
    private val context: Context,
    private val profileManager: ProfileManager
) {
    private val gson = Gson()
    private fun outboxKey() = profileManager.profileStringKey("trakt_outbox_items")

    suspend fun loadAll(): List<TraktOutboxItem> {
        val prefs = context.traktOutboxDataStore.data.first()
        val json = prefs[outboxKey()] ?: return emptyList()
        return decode(json)
    }

    suspend fun enqueue(item: TraktOutboxItem) {
        context.traktOutboxDataStore.edit { prefs ->
            val current = decode(prefs[outboxKey()])
            current.add(item)
            prefs[outboxKey()] = gson.toJson(current)
        }
    }

    suspend fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.traktOutboxDataStore.edit { prefs ->
            val current = decode(prefs[outboxKey()])
            val updated = current.filterNot { it.id in ids }
            prefs[outboxKey()] = gson.toJson(updated)
        }
    }

    suspend fun incrementAttempts(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.traktOutboxDataStore.edit { prefs ->
            val current = decode(prefs[outboxKey()])
            val updated = current.map { item ->
                if (item.id in ids) item.copy(attempts = item.attempts + 1) else item
            }
            prefs[outboxKey()] = gson.toJson(updated)
        }
    }

    private fun decode(json: String?): MutableList<TraktOutboxItem> {
        if (json.isNullOrBlank()) return mutableListOf()
        val type = TypeToken.getParameterized(MutableList::class.java, TraktOutboxItem::class.java).type
        return gson.fromJson(json, type) ?: mutableListOf()
    }
}
