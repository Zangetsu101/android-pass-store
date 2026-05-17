package com.zangetsu101.pass.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefsDataStore by preferencesDataStore("app_prefs")

@Singleton
class AppPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val store = context.appPrefsDataStore

        // empty string = not configured (onboarding needed); non-empty = configured
        val remoteUrl: Flow<String> = store.data.map { it[KEY_REMOTE_URL] ?: "" }
        val sessionTimeoutMinutes: Flow<Int> = store.data.map { it[KEY_SESSION_TIMEOUT] ?: 5 }
        val sshPublicKey: Flow<String?> = store.data.map { it[KEY_SSH_PUBLIC_KEY] }
        val gpgImported: Flow<Boolean> = store.data.map { it[KEY_GPG_IMPORTED] ?: false }
        val clipboardTimeoutSeconds: Flow<Int> = store.data.map { it[KEY_CLIPBOARD_TIMEOUT] ?: 45 }
        val defaultViewTree: Flow<Boolean> = store.data.map { it[KEY_DEFAULT_VIEW_TREE] ?: true }
        val sessionLastTouched: Flow<Long> = store.data.map { it[KEY_SESSION_LAST_TOUCHED] ?: -1L }

        suspend fun setRemoteUrl(url: String) = store.edit { it[KEY_REMOTE_URL] = url }

        suspend fun clearRemoteUrl() = store.edit { it.remove(KEY_REMOTE_URL) }

        suspend fun setSessionTimeout(minutes: Int) = store.edit { it[KEY_SESSION_TIMEOUT] = minutes }

        suspend fun setSshPublicKey(key: String) = store.edit { it[KEY_SSH_PUBLIC_KEY] = key }

        suspend fun setGpgImported(done: Boolean) = store.edit { it[KEY_GPG_IMPORTED] = done }

        suspend fun setClipboardTimeout(seconds: Int) = store.edit { it[KEY_CLIPBOARD_TIMEOUT] = seconds }

        suspend fun setDefaultViewTree(tree: Boolean) = store.edit { it[KEY_DEFAULT_VIEW_TREE] = tree }

        suspend fun setSessionLastTouched(timestamp: Long) = store.edit { it[KEY_SESSION_LAST_TOUCHED] = timestamp }

        suspend fun clearAll() = store.edit { it.clear() }

        companion object {
            private val KEY_REMOTE_URL = stringPreferencesKey("remote_url")
            private val KEY_SESSION_TIMEOUT = intPreferencesKey("session_timeout_minutes")
            private val KEY_SSH_PUBLIC_KEY = stringPreferencesKey("ssh_public_key")
            private val KEY_GPG_IMPORTED = booleanPreferencesKey("gpg_imported")
            private val KEY_CLIPBOARD_TIMEOUT = intPreferencesKey("clipboard_timeout_seconds")
            private val KEY_DEFAULT_VIEW_TREE = booleanPreferencesKey("default_view_tree")
            private val KEY_SESSION_LAST_TOUCHED = longPreferencesKey("session_last_touched")
        }
    }
