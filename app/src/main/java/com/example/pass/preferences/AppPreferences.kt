package com.example.pass.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefsDataStore by preferencesDataStore("app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.appPrefsDataStore

    // null = not yet configured (onboarding needed); non-null = configured
    val remoteUrl: Flow<String?> = store.data.map { it[KEY_REMOTE_URL] }
    val sessionTimeoutMinutes: Flow<Int> = store.data.map { it[KEY_SESSION_TIMEOUT] ?: 5 }

    suspend fun setRemoteUrl(url: String) = store.edit { it[KEY_REMOTE_URL] = url }
    suspend fun clearRemoteUrl() = store.edit { it.remove(KEY_REMOTE_URL) }
    suspend fun setSessionTimeout(minutes: Int) = store.edit { it[KEY_SESSION_TIMEOUT] = minutes }

    companion object {
        private val KEY_REMOTE_URL = stringPreferencesKey("remote_url")
        private val KEY_SESSION_TIMEOUT = intPreferencesKey("session_timeout_minutes")
    }
}
