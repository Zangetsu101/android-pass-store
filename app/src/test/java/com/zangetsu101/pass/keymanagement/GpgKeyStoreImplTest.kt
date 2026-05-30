package com.zangetsu101.pass.keymanagement

import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStoreImpl
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.session.SessionError
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

private class InMemoryPlainCryptoStore : PlainCryptoStore {
    private var data: ByteArray? = null

    override fun store(data: ByteArray) {
        this.data = data.copyOf()
    }

    override fun get(): ByteArray = checkNotNull(data) { "Nothing stored" }

    override fun exists(): Boolean = data != null

    override fun delete() {
        data = null
    }
}

class GpgKeyStoreImplTest {
    private lateinit var gpgKeyStore: GpgKeyStoreImpl

    @BeforeEach
    fun setup() {
        gpgKeyStore = GpgKeyStoreImpl(InMemoryPlainCryptoStore())
    }

    private fun armoredProtectedKey(
        passphrase: String = "correct",
        uid: String = "Test <test@example.com>",
    ): String = PGPainless.getInstance().toAsciiArmor(PGPainless().generateKey().modernKeyRing(uid, passphrase))

    private fun armoredUnprotectedKey(): String =
        PGPainless.getInstance().toAsciiArmor(PGPainless().generateKey().modernKeyRing("Test <test@example.com>"))

    // importGpgKey

    @Test
    fun `importGpgKey with passphrase-protected key stores key`() {
        gpgKeyStore.importGpgKey(armoredProtectedKey())

        assertTrue(gpgKeyStore.exists())
    }

    @Test
    fun `importGpgKey with unprotected key throws NoPassphrase`() {
        assertThrows<KeyImportError.NoPassphrase> {
            gpgKeyStore.importGpgKey(armoredUnprotectedKey())
        }
    }

    @Test
    fun `importGpgKey with malformed text throws Malformed`() {
        assertThrows<KeyImportError.Malformed> {
            gpgKeyStore.importGpgKey("not a pgp key at all")
        }
    }

    // armorGpgKey

    @Test
    fun `armorGpgKey with binary PGP bytes returns armored string and round-trip key IDs match`() {
        val key = PGPainless().generateKey().modernKeyRing("Test <test@example.com>")
        val originalRing = key.pgpSecretKeyRing
        val binaryBytes = ByteArrayOutputStream().also { originalRing.encode(it) }.toByteArray()

        val armored = gpgKeyStore.armorGpgKey(binaryBytes)

        assertTrue(armored.contains("BEGIN PGP PRIVATE KEY BLOCK"))
        val resultRing = PGPainless().readKey().parseKey(armored).pgpSecretKeyRing
        val originalId = originalRing.firstOrNull { it.isMasterKey }?.keyID
        val resultId = resultRing.firstOrNull { it.isMasterKey }?.keyID
        assertEquals(originalId, resultId)
    }

    @Test
    fun `armorGpgKey with malformed bytes throws Malformed`() {
        assertThrows<KeyImportError.Malformed> {
            gpgKeyStore.armorGpgKey("garbage bytes".toByteArray())
        }
    }

    // validatePassphrase

    @Test
    fun `validatePassphrase with correct passphrase does not throw`() {
        gpgKeyStore.importGpgKey(armoredProtectedKey("correct"))
        gpgKeyStore.validatePassphrase("correct")
    }

    @Test
    fun `validatePassphrase with wrong passphrase throws WrongPassphrase`() {
        gpgKeyStore.importGpgKey(armoredProtectedKey("correct"))
        assertThrows<SessionError.WrongPassphrase> {
            gpgKeyStore.validatePassphrase("wrong")
        }
    }

    @Test
    fun `validatePassphrase with no GPG key throws IllegalStateException`() {
        assertThrows<IllegalStateException> {
            gpgKeyStore.validatePassphrase("any")
        }
    }

    // loadAndUnlock

    @Test
    fun `loadAndUnlock with correct passphrase returns key usable for decryption`() {
        val passphrase = "correct"
        val protectedKey = PGPainless().generateKey().modernKeyRing("Test <test@example.com>", passphrase)
        gpgKeyStore.importGpgKey(PGPainless.getInstance().toAsciiArmor(protectedKey))

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

        val unlockedKey = gpgKeyStore.loadAndUnlock(passphrase)

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
        gpgKeyStore.importGpgKey(armoredProtectedKey("correct"))
        assertThrows<SessionError.WrongPassphrase> {
            gpgKeyStore.loadAndUnlock("wrong")
        }
    }

    // getGpgKeyInfo

    @Test
    fun `getGpgKeyInfo returns null when no key stored`() {
        assertNull(gpgKeyStore.getGpgKeyInfo())
    }

    @Test
    fun `getGpgKeyInfo returns keyId and uid with correct format`() {
        gpgKeyStore.importGpgKey(armoredProtectedKey("pass", "Alice <alice@example.com>"))

        val info = gpgKeyStore.getGpgKeyInfo()

        assertNotNull(info)
        requireNotNull(info)
        val (keyId, uid) = info
        assertTrue(keyId.matches(Regex("[0-9A-F]{4} [0-9A-F]{4}")), "keyId format mismatch: $keyId")
        assertTrue(uid.contains("alice@example.com"), "uid should contain email: $uid")
    }

    @Test
    fun `getGpgKeyInfo returns null for corrupted stored data`() {
        gpgKeyStore.store("corrupted content - not a valid pgp key".toByteArray())

        assertNull(gpgKeyStore.getGpgKeyInfo())
    }
}
