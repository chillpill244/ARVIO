package com.muvio.shared.storage

/**
 * Simple key-value store backed by SharedPreferences on Android and
 * NSUserDefaults on iOS. Provides the minimal surface needed by repositories
 * for persisting JSON blobs and primitive settings values.
 */
expect class PlatformPreferences(name: String) {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun getStringOrDefault(key: String, default: String): String
}
