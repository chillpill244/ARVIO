package com.arflix.tv.data.repository

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

interface PreferenceStore {
    val settings: DataStore<Preferences>
    val mediaCategory: DataStore<Preferences>
    
    // Legacy support for SharedPreferences (we use this for language and logo cache syncing)
    fun getSharedPreferences(name: String): SharedPreferences
}
