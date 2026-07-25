package com.arflix.tv.data.repository
import com.arflix.tv.shared.repository.ProfileManager
import com.arflix.tv.shared.repository.AuthRepository


import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

interface PreferenceStore {
    val settings: DataStore<Preferences>
    val mediaCategory: DataStore<Preferences>
    
    // Legacy support for SharedPreferences (we use this for language and logo cache syncing)
    fun getSharedPreferences(name: String): SharedPreferences
}
