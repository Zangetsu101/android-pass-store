// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement

import com.zangetsu101.pass.keymanagement.crypto.CryptoStore
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyManager
    @Inject
    constructor(
        private val sessionOperations: SessionOperations,
        private val cryptoStores: Set<@JvmSuppressWildcards CryptoStore>,
    ) : KeyManagement {
        override fun clearAllKeys() {
            sessionOperations.endSession()
            cryptoStores.forEach { it.delete() }
        }
    }