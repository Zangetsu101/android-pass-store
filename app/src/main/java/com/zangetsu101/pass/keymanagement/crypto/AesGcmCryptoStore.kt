// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS_PREFIX = "passdroid_wrapping_"
private const val GCM_TAG_LENGTH = 128
private const val GCM_IV_LENGTH = 12

internal class AesGcmCryptoStore(
    private val context: Context,
    private val name: String,
) : PlainCryptoStore {
    private fun keysDir(): File = File(context.filesDir, "keys").also { it.mkdirs() }

    private fun blobFile(): File = File(keysDir(), "$name.enc")

    private fun keystoreAlias() = "$KEY_ALIAS_PREFIX$name"

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val alias = keystoreAlias()
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    override fun store(data: ByteArray) {
        val key = getOrCreateWrappingKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        // blob layout: [12 bytes IV][ciphertext+tag]
        blobFile().writeBytes(iv + ciphertext)
    }

    override fun get(): ByteArray {
        val key = getOrCreateWrappingKey()
        val blob = blobFile().readBytes()
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    override fun exists(): Boolean = blobFile().exists()

    override fun delete() {
        blobFile().delete()
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val alias = keystoreAlias()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}