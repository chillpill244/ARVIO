package com.arflix.tv.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File

private var _settingsDataStore: DataStore<Preferences>? = null
val Context.settingsDataStore: DataStore<Preferences>
    get() {
        if (_settingsDataStore == null) {
            _settingsDataStore = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/settings_prefs.preferences_pb")
            }
        }
        return _settingsDataStore!!
    }

private var _traktDataStore: DataStore<Preferences>? = null
val Context.traktDataStore: DataStore<Preferences>
    get() {
        if (_traktDataStore == null) {
            _traktDataStore = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/trakt_prefs.preferences_pb")
            }
        }
        return _traktDataStore!!
    }

private var _profilesDataStore: DataStore<Preferences>? = null
val Context.profilesDataStore: DataStore<Preferences>
    get() {
        if (_profilesDataStore == null) {
            _profilesDataStore = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/profiles_prefs.preferences_pb")
            }
        }
        return _profilesDataStore!!
    }

private var _authDataStore: DataStore<Preferences>? = null
val Context.authDataStore: DataStore<Preferences>
    get() {
        if (_authDataStore == null) {
            _authDataStore = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/auth_prefs.preferences_pb")
            }
        }
        return _authDataStore!!
    }

private var _mediaCategoryPreferences: DataStore<Preferences>? = null
val Context.mediaCategoryPreferences: DataStore<Preferences>
    get() {
        if (_mediaCategoryPreferences == null) {
            _mediaCategoryPreferences = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/media_category_preferences.preferences_pb")
            }
        }
        return _mediaCategoryPreferences!!
    }

private var _streamDataStore: DataStore<Preferences>? = null
val Context.streamDataStore: DataStore<Preferences>
    get() {
        if (_streamDataStore == null) {
            _streamDataStore = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/stream_prefs.preferences_pb")
            }
        }
        return _streamDataStore!!
    }

private var _traktOutboxDataStore: DataStore<Preferences>? = null
val Context.traktOutboxDataStore: DataStore<Preferences>
    get() {
        if (_traktOutboxDataStore == null) {
            _traktOutboxDataStore = PreferenceDataStoreFactory.create {
                File(this.applicationContext.filesDir, "datastore/trakt_outbox.preferences_pb")
            }
        }
        return _traktOutboxDataStore!!
    }
