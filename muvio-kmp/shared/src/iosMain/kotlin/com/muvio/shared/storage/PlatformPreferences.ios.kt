package com.muvio.shared.storage

import platform.Foundation.NSUserDefaults

actual class PlatformPreferences actual constructor(private val name: String) {

    private val defaults: NSUserDefaults = NSUserDefaults(suiteName = name)
        ?: NSUserDefaults.standardUserDefaults

    actual suspend fun getString(key: String): String? =
        defaults.stringForKey(key)

    actual suspend fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    actual suspend fun getStringOrDefault(key: String, default: String): String =
        defaults.stringForKey(key) ?: default
}
