// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.di.AppBackgroundScope
import com.zangetsu101.pass.keymanagement.crypto.BiometricCryptoStore
import com.zangetsu101.pass.preferences.AppPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SessionManager
    @Inject
    constructor(
        private val keyStore: BiometricCryptoStore,
        private val biometricPrompter: BiometricPrompter,
        private val sessionTimer: SessionTimer,
        private val appPreferences: AppPreferences,
        @AppBackgroundScope private val scope: CoroutineScope,
        @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : SessionOperations {
        private var timeoutJob: Job? = null

        private val _sessionState = MutableStateFlow<SessionState>(SessionState.Inactive(EndReason.MANUAL))
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        init {
            val lastTouched = runBlocking { appPreferences.sessionLastTouched.first() }
            val hasSession = keyStore.exists()

            if (lastTouched != -1L && hasSession) {
                _sessionState.value = SessionState.Active
                sessionTimer.start()
                scope.launch {
                    val timeoutMs = appPreferences.sessionTimeoutMinutes.first() * 60_000L
                    val elapsed = System.currentTimeMillis() - lastTouched
                    startTimeout(timeoutMs - elapsed)
                }
            }

            scope.launch {
                appPreferences.sessionTimeoutMinutes.distinctUntilChanged().drop(1).collect {
                    if (sessionState.value is SessionState.Active) endSession(EndReason.TIMEOUT_CHANGED)
                }
            }
        }

        override suspend fun createSession(passphrase: String) {
            withContext(ioDispatcher) {
                if (passphrase.toByteArray(Charsets.UTF_8).size > keyStore.maxBytes) {
                    throw SessionError.PassphraseTooLong()
                }
                keyStore.store(passphrase.toByteArray(Charsets.UTF_8))
                appPreferences.setSessionLastTouched(System.currentTimeMillis())
            }
            _sessionState.value = SessionState.Active
            touchSession()
        }

        override fun endSession(reason: EndReason) {
            timeoutJob?.cancel()
            keyStore.delete()
            _sessionState.value = SessionState.Inactive(reason)
            sessionTimer.stop()
        }

        override fun touchSession() {
            sessionTimer.start()
            timeoutJob?.cancel()
            scope.launch {
                val timeoutMs = appPreferences.sessionTimeoutMinutes.first() * 60_000L
                if (timeoutMs <= 0L) return@launch
                appPreferences.setSessionLastTouched(System.currentTimeMillis())
                startTimeout(timeoutMs)
            }
        }

        private fun startTimeout(delayMs: Long) {
            timeoutJob?.cancel()
            if (delayMs <= 0L) {
                endSession(EndReason.TIMEOUT)
                return
            }
            timeoutJob =
                scope.launch {
                    delay(timeMillis = delayMs)
                    endSession(EndReason.TIMEOUT)
                }
        }

        override suspend fun getPassphrase(activity: FragmentActivity): String {
            if (_sessionState.value !is SessionState.Active) throw SessionError.NoActiveSession()
            val cipher =
                withContext(ioDispatcher) {
                    try {
                        keyStore.getDecryptCipher()
                    } catch (e: KeyPermanentlyInvalidatedException) {
                        endSession(EndReason.BIOMETRIC_CHANGED)
                        throw SessionError.NoActiveSession()
                    }
                }
            val authenticatedCipher =
                withContext(Dispatchers.Main) {
                    biometricPrompter.prompt(activity, cipher)
                }
            val passphrase =
                withContext(ioDispatcher) {
                    String(keyStore.get(authenticatedCipher), Charsets.UTF_8)
                }
            touchSession()
            return passphrase
        }
    }