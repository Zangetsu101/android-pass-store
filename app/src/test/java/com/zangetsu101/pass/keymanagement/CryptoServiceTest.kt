package com.zangetsu101.pass.keymanagement

import android.content.Context
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.session.SessionError
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class CryptoServiceTest {
    private lateinit var tmpDir: File
    private lateinit var cryptoService: CryptoService

    @BeforeEach
    fun setup() {
        tmpDir = Files.createTempDirectory("crypto_test").toFile()
        val context = mockk<Context>()
        every { context.filesDir } returns tmpDir
        cryptoService = CryptoService(context, mockk())
    }

    @AfterEach
    fun teardown() {
        tmpDir.deleteRecursively()
    }

    private fun armoredProtectedKey(
        passphrase: String = "correct",
        uid: String = "Test <test@example.com>",
    ): String = PGPainless.getInstance().toAsciiArmor(PGPainless().generateKey().modernKeyRing(uid, passphrase))

    private fun armoredUnprotectedKey(): String =
        PGPainless.getInstance().toAsciiArmor(PGPainless().generateKey().modernKeyRing("Test <test@example.com>"))

    private fun writeKeyToDisk(armoredKey: String) {
        File(tmpDir, "keys").mkdirs()
        File(tmpDir, "keys/$GPG_KEY_FILENAME").writeText(armoredKey)
    }

    // importGpgKey

    @Test
    fun `importGpgKey with passphrase-protected key writes file to keys dir`() {
        cryptoService.importGpgKey(armoredProtectedKey())

        assertTrue(File(tmpDir, "keys/$GPG_KEY_FILENAME").exists())
    }

    @Test
    fun `importGpgKey with unprotected key throws NoPassphrase`() {
        assertThrows<KeyImportError.NoPassphrase> {
            cryptoService.importGpgKey(armoredUnprotectedKey())
        }
    }

    @Test
    fun `importGpgKey with malformed text throws Malformed`() {
        assertThrows<KeyImportError.Malformed> {
            cryptoService.importGpgKey("not a pgp key at all")
        }
    }

    // armorGpgKey

    @Test
    fun `armorGpgKey with binary PGP bytes returns armored string and round-trip key IDs match`() {
        val key = PGPainless().generateKey().modernKeyRing("Test <test@example.com>")
        val originalRing = key.pgpSecretKeyRing
        val binaryBytes = ByteArrayOutputStream().also { originalRing.encode(it) }.toByteArray()

        val armored = cryptoService.armorGpgKey(binaryBytes)

        assertTrue(armored.contains("BEGIN PGP PRIVATE KEY BLOCK"))
        val resultRing = PGPainless().readKey().parseKey(armored).pgpSecretKeyRing
        val originalId = originalRing.firstOrNull { it.isMasterKey }?.keyID
        val resultId = resultRing.firstOrNull { it.isMasterKey }?.keyID
        assertEquals(originalId, resultId)
    }

    @Test
    fun `armorGpgKey with malformed bytes throws Malformed`() {
        assertThrows<KeyImportError.Malformed> {
            cryptoService.armorGpgKey("garbage bytes".toByteArray())
        }
    }

    // validatePassphrase

    @Test
    fun `validatePassphrase with correct passphrase does not throw`() {
        writeKeyToDisk(armoredProtectedKey("correct"))
        cryptoService.validatePassphrase("correct")
    }

    @Test
    fun `validatePassphrase with wrong passphrase throws WrongPassphrase`() {
        writeKeyToDisk(armoredProtectedKey("correct"))
        assertThrows<SessionError.WrongPassphrase> {
            cryptoService.validatePassphrase("wrong")
        }
    }

    @Test
    fun `validatePassphrase with no GPG file throws IllegalStateException`() {
        assertThrows<IllegalStateException> {
            cryptoService.validatePassphrase("any")
        }
    }

    // loadAndUnlock

    @Test
    fun `loadAndUnlock with correct passphrase returns key usable for decryption`() {
        val passphrase = "correct"
        val protectedKey = PGPainless().generateKey().modernKeyRing("Test <test@example.com>", passphrase)
        writeKeyToDisk(PGPainless.getInstance().toAsciiArmor(protectedKey))

        val plaintext = "secret-test-content"
        val ciphertext =
            ByteArrayOutputStream()
                .also { buf ->
                    val encStream =
                        PGPainless()
                            .generateMessage()
                            .onOutputStream(buf)
                            .withOptions(
                                ProducerOptions.encrypt(
                                    EncryptionOptions.encryptCommunications().addRecipient(protectedKey),
                                ),
                            )
                    encStream.use { it.write(plaintext.toByteArray()) }
                }.toByteArray()

        val unlockedKey = cryptoService.loadAndUnlock(passphrase)

        val output = ByteArrayOutputStream()
        val decryptStream =
            PGPainless()
                .processMessage()
                .onInputStream(ByteArrayInputStream(ciphertext))
                .withOptions(ConsumerOptions.get().addDecryptionKey(unlockedKey))
        decryptStream.use { it.copyTo(output) }

        assertEquals(plaintext, output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `loadAndUnlock with wrong passphrase throws WrongPassphrase`() {
        writeKeyToDisk(armoredProtectedKey("correct"))
        assertThrows<SessionError.WrongPassphrase> {
            cryptoService.loadAndUnlock("wrong")
        }
    }

    // getGpgKeyInfo

    @Test
    fun `getGpgKeyInfo returns null when no file on disk`() {
        assertNull(cryptoService.getGpgKeyInfo())
    }

    @Test
    fun `getGpgKeyInfo returns keyId and uid with correct format`() {
        writeKeyToDisk(armoredProtectedKey("pass", "Alice <alice@example.com>"))

        val info = cryptoService.getGpgKeyInfo()

        assertNotNull(info)
        requireNotNull(info)
        val (keyId, uid) = info
        assertTrue(keyId.matches(Regex("[0-9A-F]{4} [0-9A-F]{4}")), "keyId format mismatch: $keyId")
        assertTrue(uid.contains("alice@example.com"), "uid should contain email: $uid")
    }

    @Test
    fun `getGpgKeyInfo returns null for corrupted file`() {
        File(tmpDir, "keys").mkdirs()
        File(tmpDir, "keys/$GPG_KEY_FILENAME").writeText("corrupted content - not a valid pgp key")

        assertNull(cryptoService.getGpgKeyInfo())
    }
}
