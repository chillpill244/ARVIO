package com.arflix.tv.data.repository
import com.arflix.tv.shared.repository.ProfileManager
import com.arflix.tv.shared.repository.AuthRepository


import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

class AndroidPreferenceStore(
    private val context: Context,
    override val settings: DataStore<Preferences>,
    override val mediaCategory: DataStore<Preferences>
) : PreferenceStore {
    
    override fun getSharedPreferences(name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
