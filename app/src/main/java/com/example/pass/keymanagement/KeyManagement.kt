package com.example.pass.keymanagement

import androidx.fragment.app.FragmentActivity
import org.bouncycastle.openpgp.PGPSecretKeyRing
import java.security.KeyPair

typealias GpgPrivateKey = PGPSecretKeyRing
typealias SshPrivateKey = KeyPair

interface KeyManagement {
    @Throws(KeyImportError::class)
    fun importGpgKey(armoredKey: String)

    @Throws(SessionError::class)
    fun startSession(passphrase: String)

    fun endSession()

    fun isSessionActive(): Boolean

    @Throws(SessionError::class)
    suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey

    @Throws(SessionError::class)
    fun getGpgKeyWithPassphrase(passphrase: String): GpgPrivateKey

    fun generateSshKey(): String

    fun getSshKey(): SshPrivateKey

    fun clearAllKeys()
}
