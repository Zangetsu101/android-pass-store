package com.example.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.passstore.PassEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecryptionImpl @Inject constructor(
    private val keyManagement: KeyManagement,
) : Decryption {

    override suspend fun decrypt(entry: PassEntry, activity: FragmentActivity): Credentials {
        val secretKeyRing = keyManagement.getGpgKey(activity)
        val plaintext = withContext(Dispatchers.IO) { decryptFile(entry, secretKeyRing) }
        val lines = plaintext.lines()
        val password = (lines.firstOrNull() ?: "").toCharArray()
        val notes = lines.drop(1).joinToString("\n").trimEnd()
        return Credentials(password = password, notes = notes)
    }

    override suspend fun decryptForAutofill(
        entry: PassEntry,
        activity: FragmentActivity,
    ): AutofillCredentials {
        val secretKeyRing = keyManagement.getGpgKey(activity)
        val plaintext = withContext(Dispatchers.IO) { decryptFile(entry, secretKeyRing) }
        val password = (plaintext.lines().firstOrNull() ?: "").toCharArray()
        return AutofillCredentials(password = password, username = entry.username)
    }

    private fun decryptFile(entry: PassEntry, secretKeyRing: PGPSecretKeyRing): String {
        val protector = SecretKeyRingProtector.unprotectedKeys()

        return try {
            val ciphertext = entry.encryptedFile.readBytes()
            val output = ByteArrayOutputStream()
            val stream = PGPainless.decryptAndOrVerify()
                .onInputStream(ByteArrayInputStream(ciphertext))
                .withOptions(
                    ConsumerOptions.get()
                        .addDecryptionKey(secretKeyRing, protector)
                )
            stream.use { it.copyTo(output) }
            if (!stream.metadata.isEncrypted) {
                throw DecryptionError("Content is not PGP-encrypted")
            }
            output.toString(Charsets.UTF_8.name())
        } catch (e: DecryptionError) {
            throw e
        } catch (e: PGPException) {
            throw DecryptionError("Decryption failed: ${e.message}", e)
        } catch (e: Exception) {
            throw DecryptionError("Decryption failed: ${e.message}", e)
        }
    }
}
