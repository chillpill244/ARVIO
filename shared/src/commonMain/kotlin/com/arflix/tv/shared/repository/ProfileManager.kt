package com.arflix.tv.shared.repository

import androidx.datastore.preferences.core.Preferences

import kotlinx.coroutines.flow.Flow

interface ProfileManager {
    val activeProfileId: Flow<String>
    fun getProfileIdSync(): String
    suspend fun getProfileId(): String
    suspend fun initialize()
    fun setCurrentProfileId(profileId: String)
    fun setCurrentProfileName(profileName: String)
    fun profileStringKey(name: String): Preferences.Key<String>
    fun profileStringKeyFor(profileId: String, name: String): Preferences.Key<String>
    fun profileLongKey(name: String): Preferences.Key<Long>
    fun profileLongKeyFor(profileId: String, name: String): Preferences.Key<Long>
    fun profileBooleanKey(name: String): Preferences.Key<Boolean>
    fun profileBooleanKeyFor(profileId: String, name: String): Preferences.Key<Boolean>
    fun getKeyPrefix(): String
    fun getProfileNameSync(): String
    fun isDefaultProfile(): Boolean
}
