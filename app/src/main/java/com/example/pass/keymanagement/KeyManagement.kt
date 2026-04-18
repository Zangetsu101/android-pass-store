package com.example.pass.keymanagement

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
     * Decrypts and returns the GPG secret key ring.
     * The returned key has no PGP passphrase; it is protected only by the Keystore blob.
     */
    fun getGpgKey(): PGPSecretKeyRing

    /**
     * Generates an Ed25519 SSH keypair, stores the private key as an encrypted blob,
     * and returns the public key formatted as an OpenSSH string ready to be registered
     * on the remote git server.
     */
    fun generateSshKey(): String

    /**
     * Decrypts and returns the SSH keypair from the encrypted blob.
     */
    fun getSshKey(): KeyPair

    fun clearAllKeys()
}
