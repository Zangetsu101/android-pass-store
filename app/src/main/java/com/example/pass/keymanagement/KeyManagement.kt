package com.example.pass.keymanagement

import androidx.fragment.app.FragmentActivity
import org.bouncycastle.openpgp.PGPSecretKeyRing
import java.security.KeyPair

interface KeyManagement {
    /**
     * Parses the armored GPG key, verifies the passphrase if provided, strips
     * PGP-level password protection, and stores the key as an AES-256-GCM blob.
     *
     * @throws KeyImportError if the key is malformed or the passphrase is wrong
     */
    @Throws(KeyImportError::class)
    fun importGpgKey(armoredKey: String, passphrase: String? = null)

    /**
     * Generates an Ed25519 SSH keypair, stores the private key as an encrypted blob,
     * and returns the public key formatted as an OpenSSH string ready to be registered
     * on the remote git server.
     */
    fun generateSshKey(): String

    /**
     * Prompts biometric if the session is not active, then returns the GPG secret key ring.
     */
    suspend fun getGpgKey(activity: FragmentActivity): PGPSecretKeyRing

    /**
     * Prompts biometric if the session is not active, then returns the SSH keypair.
     */
    suspend fun getSshKey(activity: FragmentActivity): KeyPair

    fun clearAllKeys()
}
