package com.arflix.tv.data.repository
import com.arflix.tv.shared.repository.ProfileManager
import com.arflix.tv.shared.repository.AuthRepository


import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.util.profilesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Manages the current active profile and provides profile-scoped preference keys.
 *
 * All repositories that need profile isolation should use this to get prefixed keys.
 * Example: Instead of "access_token", use profileManager.profileKey("access_token")
 * which returns "profile_<id>_access_token"
 */
class ProfileManagerImpl constructor(
    private val context: Context,
    private val profileRepository: ProfileRepository
) : com.arflix.tv.shared.repository.ProfileManager {
    // Default profile ID used when no profile is selected (for backwards compatibility)
    private val DEFAULT_PROFILE_ID = "default"

    // Cache the current profile ID for synchronous access
    private val _currentProfileId = MutableStateFlow<String>(DEFAULT_PROFILE_ID)
    val currentProfileId: StateFlow<String> = _currentProfileId.asStateFlow()
    private val _currentProfileName = MutableStateFlow<String>(DEFAULT_PROFILE_ID)
    val currentProfileName: StateFlow<String> = _currentProfileName.asStateFlow()

    /**
     * Flow of the current profile ID
     */
    override val activeProfileId: Flow<String> = profileRepository.activeProfileId.map { id ->
        val profileId = id ?: DEFAULT_PROFILE_ID
        _currentProfileId.value = profileId
        profileId
    }

    val activeProfileName: Flow<String> = profileRepository.activeProfile.map { profile ->
        val profileName = profile?.name?.lowercase()?.trim().takeUnless { it.isNullOrBlank() }
            ?: DEFAULT_PROFILE_ID
        _currentProfileName.value = profileName
        profileName
    }

    /**
     * Get the current profile ID synchronously (cached value)
     * Use this when you need immediate access without suspend
     */
    override fun getProfileIdSync(): String = _currentProfileId.value

    /**
     * Get the current profile ID (suspend version, always fresh)
     */
    override suspend fun getProfileId(): String {
        val id = profileRepository.getActiveProfileId() ?: DEFAULT_PROFILE_ID
        _currentProfileId.value = id
        return id
    }

    /**
     * Initialize the profile manager - call this early in app startup
     */
    override suspend fun initialize() {
        val active = profileRepository.getActiveProfile()
        val id = active?.id ?: DEFAULT_PROFILE_ID
        _currentProfileId.value = id
        _currentProfileName.value = active?.name?.lowercase()?.trim().takeUnless { it.isNullOrBlank() }
            ?: id
    }

    /**
     * Directly update the current profile ID - use when switching profiles
     * to ensure the cached ID is updated before any profile-scoped operations
     */
    override fun setCurrentProfileId(profileId: String) {
        _currentProfileId.value = profileId
        if (_currentProfileName.value.isBlank()) {
            _currentProfileName.value = profileId
        }
    }

    override fun setCurrentProfileName(profileName: String) {
        val normalized = profileName.lowercase().trim()
        if (normalized.isNotBlank()) {
            _currentProfileName.value = normalized
        }
    }

    // ========== Profile-Scoped Key Generators ==========

    /**
     * Create a profile-scoped string preference key
     */
    override fun profileStringKey(name: String): Preferences.Key<String> {
        return stringPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped string preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    override fun profileStringKeyFor(profileId: String, name: String): Preferences.Key<String> {
        return stringPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Create a profile-scoped long preference key
     */
    override fun profileLongKey(name: String): Preferences.Key<Long> {
        return longPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped long preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    override fun profileLongKeyFor(profileId: String, name: String): Preferences.Key<Long> {
        return longPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Create a profile-scoped boolean preference key
     */
    override fun profileBooleanKey(name: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped boolean preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    override fun profileBooleanKeyFor(profileId: String, name: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Get the key prefix for the current profile
     */
    override fun getKeyPrefix(): String = "profile_${getProfileIdSync()}_"

    /**
     * Get the current profile name synchronously (for cross-device matching).
     * Falls back to profile ID if name lookup fails.
     */
    override fun getProfileNameSync(): String {
        return _currentProfileName.value.takeUnless { it.isBlank() } ?: getProfileIdSync()
    }

    /**
     * Check if this is the default profile (no user profile selected)
     */
    override fun isDefaultProfile(): Boolean = getProfileIdSync() == DEFAULT_PROFILE_ID
}
