package com.muvio.shared.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformPreferences actual constructor(name: String) {

    // Injected via MuvioKoinContext.androidContext() before Koin starts.
    private val prefs: SharedPreferences by lazy {
        MuvioAndroidContext.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    actual suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    actual suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    actual suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    actual suspend fun getStringOrDefault(key: String, default: String): String =
        getString(key) ?: default
}

/** Holds a reference to the Android application context for use by expect/actual impls. */
object MuvioAndroidContext {
    lateinit var applicationContext: Context
}
