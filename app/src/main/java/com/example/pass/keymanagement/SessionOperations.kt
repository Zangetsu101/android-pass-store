package com.example.pass.keymanagement

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.StateFlow

interface SessionOperations {
    val sessionState: StateFlow<SessionState>

    @Throws(SessionError::class)
    suspend fun createSession(passphrase: String)

    fun endSession(reason: EndReason = EndReason.MANUAL)

    fun touchSession()

    fun isSessionActive(): Boolean

    @Throws(SessionError::class)
    suspend fun getPassphrase(activity: FragmentActivity): String
}
