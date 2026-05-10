package com.example.pass.keymanagement

import androidx.fragment.app.FragmentActivity

interface CryptoOperations {
    @Throws(KeyImportError::class)
    fun importGpgKey(armoredKey: String)

    @Throws(SessionError::class)
    suspend fun startSession(passphrase: String)

    @Throws(SessionError::class)
    suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey

    fun generateSshKey(): String

    fun getSshKey(): SshPrivateKey

    fun clearAllKeys()
}
