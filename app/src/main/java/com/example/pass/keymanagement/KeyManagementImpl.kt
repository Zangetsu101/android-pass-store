package com.example.pass.keymanagement

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import javax.inject.Inject
import javax.inject.Singleton

internal const val BLOB_GPG_KEY = "gpg_key"
internal const val BLOB_SSH_KEY = "ssh_key"

@Singleton
class KeyManagementImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : KeyManagement {

    internal val blobStore = KeyBlobStore(context)

    @Throws(KeyImportError::class)
    override fun importGpgKey(armoredKey: String, passphrase: String?) {
        val keys: PGPSecretKeyRing = try {
            PGPainless.readKeyRing().secretKeyRing(armoredKey)
                ?: throw KeyImportError("No secret key found in armored input")
        } catch (e: KeyImportError) {
            throw e
        } catch (e: Exception) {
            throw KeyImportError("Malformed armored key: ${e.message}", e)
        }

        val oldProtector = if (passphrase != null) {
            SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(passphrase))
        } else {
            SecretKeyRingProtector.unprotectedKeys()
        }

        // Strip passphrase — key is protected by the Keystore-backed AES blob instead
        val strippedKeys: PGPSecretKeyRing = try {
            PGPainless.modifyKeyRing(keys)
                .changePassphrase(null, oldProtector, SecretKeyRingProtector.unprotectedKeys())
                .done()
        } catch (e: PGPException) {
            throw KeyImportError("Wrong passphrase or unsupported key format: ${e.message}", e)
        }

        blobStore.encrypt(BLOB_GPG_KEY, strippedKeys.encoded)
    }

    override fun getGpgKey(): PGPSecretKeyRing {
        val bytes = blobStore.decrypt(BLOB_GPG_KEY)
        return PGPainless.readKeyRing().secretKeyRing(bytes)
            ?: error("Corrupted GPG key blob")
    }

    override fun clearAllKeys() {
        blobStore.deleteAll()
    }
}
