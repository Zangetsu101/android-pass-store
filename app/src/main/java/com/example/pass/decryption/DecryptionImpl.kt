package com.example.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.passstore.PassEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.openpgp.PGPException
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

    override suspend fun decrypt(entry: PassEntry, activity: FragmentActivity): Credentials =
        withContext(Dispatchers.IO) {
            val plaintext = decryptFile(entry, activity)
            val lines = plaintext.lines()
            val password = (lines.firstOrNull() ?: "").toCharArray()
            val notes = lines.drop(1).joinToString("\n").trimEnd()
            Credentials(password = password, notes = notes)
        }

    override suspend fun decryptForAutofill(
        entry: PassEntry,
        activity: FragmentActivity,
    ): AutofillCredentials = withContext(Dispatchers.IO) {
        val plaintext = decryptFile(entry, activity)
        val password = (plaintext.lines().firstOrNull() ?: "").toCharArray()
        AutofillCredentials(password = password, username = entry.username)
    }

    private suspend fun decryptFile(entry: PassEntry, activity: FragmentActivity): String {
        val secretKeyRing = keyManagement.getGpgKey(activity)
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
            output.toString(Charsets.UTF_8.name())
        } catch (e: PGPException) {
            throw DecryptionError("Decryption failed: ${e.message}", e)
        } catch (e: Exception) {
            throw DecryptionError("Decryption failed: ${e.message}", e)
        }
    }
}
