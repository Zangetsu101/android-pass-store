package com.zangetsu101.pass.keymanagement.crypto

interface CryptoStore {
    fun exists(): Boolean

    fun delete()
}
