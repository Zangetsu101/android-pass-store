package com.zangetsu101.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyProvider
import com.zangetsu101.pass.passstore.PassEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.openpgp.PGPException
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.exception.MissingDecryptionMethodException
import org.pgpainless.exception.WrongPassphraseException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DecryptionImpl(
    private val gpgKeyProvider: GpgKeyProvider,
) : Decryption {
    override suspend fun decrypt(
        entry: PassEntry,
        activity: FragmentActivity,
    ): Credentials {
        val secretKeyRing = gpgKeyProvider.getGpgKey(activity)
        val plaintext = withContext(Dispatchers.IO) { decryptFile(entry, secretKeyRing) }
        val lines = plaintext.lines()
        val password = (lines.firstOrNull() ?: "").toCharArray()
        val notes = lines.drop(1).joinToString("\n").trimEnd()
        return Credentials(password = password, notes = notes, username = entry.username)
    }

    private fun decryptFile(
        entry: PassEntry,
        secretKeyRing: GpgPrivateKey,
    ): String =
        try {
            val ciphertext = entry.encryptedFile.readBytes()
            val output = ByteArrayOutputStream()
            val stream =
                PGPainless
                    .getInstance()
                    .processMessage()
                    .onInputStream(ByteArrayInputStream(ciphertext))
                    .withOptions(
                        ConsumerOptions
                            .get()
                            .addDecryptionKey(secretKeyRing),
                    )
            stream.use { it.copyTo(output) }
            if (!stream.metadata.isEncrypted) {
                throw DecryptionError("Content is not PGP-encrypted")
            }
            output.toString(Charsets.UTF_8.name())
        } catch (e: DecryptionError) {
            throw e
        } catch (e: MissingDecryptionMethodException) {
            // Ring is valid and usable, but this entry was encrypted to a different key.
            throw DecryptionError(
                "Couldn't decrypt this entry with the imported keyring. It was likely encrypted to a " +
                    "different key — import the key this store was encrypted to, or re-encrypt the store to your key.",
                e,
            )
        } catch (e: WrongPassphraseException) {
            throw DecryptionError("Wrong passphrase.", e)
        } catch (e: PGPException) {
            throw DecryptionError("Decryption failed: ${e.message}", e)
        } catch (e: Exception) {
            throw DecryptionError("Decryption failed: ${e.message}", e)
        }
}
