// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.crypto

interface CryptoStore {
    fun exists(): Boolean

    fun delete()
}
