package com.example.pass.keymanagement

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore("session")

private val KEY_TIMEOUT_MS = longPreferencesKey("session_timeout_ms")
private val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

@Singleton
class SessionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile private var lastAuthTime: Long = 0L

        fun recordAuth() {
            lastAuthTime = System.currentTimeMillis()
        }

        fun invalidate() {
            lastAuthTime = 0L
        }

        suspend fun isSessionActive(): Boolean {
            val timeout = context.sessionDataStore.data.first()[KEY_TIMEOUT_MS] ?: DEFAULT_TIMEOUT_MS
            return (System.currentTimeMillis() - lastAuthTime) < timeout
        }

        suspend fun setTimeoutMs(ms: Long) {
            context.sessionDataStore.edit { it[KEY_TIMEOUT_MS] = ms }
        }

        suspend fun getTimeoutMs(): Long = context.sessionDataStore.data.first()[KEY_TIMEOUT_MS] ?: DEFAULT_TIMEOUT_MS
    }
