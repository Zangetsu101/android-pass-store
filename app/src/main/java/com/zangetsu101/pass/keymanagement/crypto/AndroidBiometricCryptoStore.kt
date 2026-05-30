package com.zangetsu101.pass.keymanagement.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject

private const val SESSION_KEY_ALIAS = "passdroid_session_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val RSA_KEY_SIZE = 2048
private const val RSA_MAX_PLAINTEXT_BYTES = RSA_KEY_SIZE / 8 - 2 * 32 - 2 // OAEP-SHA256: 190 bytes
private const val RSA_CIPHER = "RSA/ECB/OAEPPadding"

// OAEP hash = SHA-256; MGF1 hash = SHA-1 (Android Keystore hardware only supports SHA-1 for MGF1)
private val RSA_OAEP_SPEC =
    OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

class AndroidBiometricCryptoStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BiometricCryptoStore {
        override val maxBytes: Int = RSA_MAX_PLAINTEXT_BYTES

        private fun keysDir(): File = File(context.filesDir, "keys").also { it.mkdirs() }

        private fun blobFile(): File = File(keysDir(), "session.enc")

        private fun keyStore() = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        override fun exists(): Boolean {
            val ks = keyStore()
            return ks.containsAlias(SESSION_KEY_ALIAS) && blobFile().exists()
        }

        override fun store(data: ByteArray) {
            val ks = keyStore()
            if (ks.containsAlias(SESSION_KEY_ALIAS)) {
                ks.deleteEntry(SESSION_KEY_ALIAS)
            }
            val spec =
                KeyGenParameterSpec
                    .Builder(SESSION_KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setKeySize(RSA_KEY_SIZE)
                    .setUserAuthenticationRequired(true)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        }
                    }.build()
            KeyPairGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
                .apply { initialize(spec) }
                .generateKeyPair()
            val publicKey = keyStore().getCertificate(SESSION_KEY_ALIAS).publicKey
            val cipher = Cipher.getInstance(RSA_CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, RSA_OAEP_SPEC)
            blobFile().writeBytes(cipher.doFinal(data))
        }

        override fun getDecryptCipher(): Cipher {
            val privateKey = keyStore().getKey(SESSION_KEY_ALIAS, null) as PrivateKey
            val cipher = Cipher.getInstance(RSA_CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, RSA_OAEP_SPEC)
            return cipher
        }

        override fun get(cipher: Cipher): ByteArray = cipher.doFinal(blobFile().readBytes())

        override fun delete() {
            val ks = keyStore()
            if (ks.containsAlias(SESSION_KEY_ALIAS)) {
                ks.deleteEntry(SESSION_KEY_ALIAS)
            }
            blobFile().delete()
        }
    }
