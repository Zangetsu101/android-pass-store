// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.di.AppBackgroundScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

internal const val BIOMETRIC_CACHE_TIMEOUT_MS = 5 * 60 * 1000L

@Singleton
class CachingPassphraseProvider
    @Inject
    constructor(
        @DirectPassphrase private val delegate: PassphraseProvider,
        private val sessionOperations: SessionOperations,
        @AppBackgroundScope private val scope: CoroutineScope,
    ) : PassphraseProvider {
        private var cachedPassphrase: String? = null
        private var cacheJob: Job? = null

        init {
            scope.launch {
                sessionOperations.sessionState.collect { state ->
                    if (state is SessionState.Inactive) {
                        cachedPassphrase = null
                        cacheJob?.cancel()
                    }
                }
            }
        }

        override suspend fun getPassphrase(activity: FragmentActivity): String {
            cachedPassphrase?.let { p ->
                sessionOperations.touchSession()
                return p
            }
            val passphrase = delegate.getPassphrase(activity)
            cachePassphrase(passphrase)
            sessionOperations.touchSession()
            return passphrase
        }

        private fun cachePassphrase(passphrase: String) {
            cachedPassphrase = passphrase
            cacheJob?.cancel()
            cacheJob =
                scope.launch {
                    delay(timeMillis = BIOMETRIC_CACHE_TIMEOUT_MS)
                    cachedPassphrase = null
                }
        }
    }