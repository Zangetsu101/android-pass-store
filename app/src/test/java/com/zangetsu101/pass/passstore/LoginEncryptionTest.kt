// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import com.zangetsu101.pass.keymanagement.gpg.PublicKeySource
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import java.io.File
import java.nio.file.Files

class LoginEncryptionTest {
    @TempDir lateinit var root: File

    private val pgp = PGPainless.getInstance()
    private val key = pgp.generateKey().modernKeyRing("Alice Example <alice@example.com>")
    private val ring = key.pgpSecretKeyRing
    private val encryption =
        LoginEncryption(
            PublicKeySource(
                PGPPublicKeyRing(ring.publicKeys.asSequence().toList()),
                ring.map { it.publicKey.keyID }.toSet(),
            ),
        )
    private val path = "team/example.com/alice.gpg"

    @Test
    fun `recipient in the entry folder wins and matches the encryption subkey ID`() {
        recipients("", "other@example.com")
        recipients("team", "other@example.com")
        val subkey = ring.publicKeys.asSequence().first { !it.isMasterKey && it.isEncryptionKey }
        recipients("team/example.com", "0x%016X".format(subkey.keyID))

        assertRoundTrip()
    }

    @Test
    fun `recipient in the parent folder matches the primary fingerprint`() {
        recipients("", "other@example.com")
        recipients("team", ring.publicKey.fingerprint.joinToString("") { "%02x".format(it) })

        assertRoundTrip()
    }

    @Test
    fun `root recipients can match by email and primary long key ID`() {
        recipients("", "Alice@EXAMPLE.com\n%016X\n".format(ring.publicKey.keyID))

        assertRoundTrip()
        assertEquals("Alice Example", encryption.author().name)
        assertEquals("alice@example.com", encryption.author().emailAddress)
    }

    @Test
    fun `shared recipients block encryption even when the root allows it`() {
        recipients("", "alice@example.com")
        recipients("team", "alice@example.com\nbob@example.com\ncarol@example.com")

        val failure = assertThrows(LoginSaveError::class.java) { encryption.encrypt(root, path, "password") }
        assertEquals("team/ is shared with 2 other keys · can't encrypt for them yet", failure.message)
    }

    @Test
    fun `missing recipients block encryption`() {
        val failure = assertThrows(LoginSaveError::class.java) { encryption.encrypt(root, path, "password") }
        assertEquals("missing .gpg-id · cannot encrypt", failure.message)
    }

    @Test
    fun `one unknown recipient is named in the rejection`() {
        recipients("", "alice@example.com\nbob@example.com")

        val failure = assertThrows(LoginSaveError::class.java) { encryption.encrypt(root, path, "password") }
        assertEquals("store is shared with 1 other key · can't encrypt for them yet", failure.message)
    }

    @Test
    fun `an empty or linked recipient file blocks the save instead of falling back`() {
        recipients("", "alice@example.com")
        recipients("team", "")
        val empty = assertThrows(LoginSaveError::class.java) { encryption.encrypt(root, path, "password") }
        assertEquals("empty .gpg-id · cannot encrypt", empty.message)

        val nearest = File(root, "team/.gpg-id").toPath()
        Files.delete(nearest)
        Files.createSymbolicLink(nearest, File(root, ".gpg-id").toPath())
        val linked = assertThrows(LoginSaveError::class.java) { encryption.encrypt(root, path, "password") }
        assertEquals("cannot read .gpg-id", linked.message)
    }

    private fun assertRoundTrip() {
        val plaintext = "secret\r\nnotes  \r\n"
        val ciphertext = encryption.encrypt(root, path, plaintext)
        val input =
            pgp
                .processMessage()
                .onInputStream(ciphertext.inputStream())
                .withOptions(ConsumerOptions.get().addDecryptionKey(key))
        assertEquals(plaintext, input.use { it.readBytes().decodeToString() })
    }

    private fun recipients(
        folder: String,
        contents: String,
    ) {
        File(root, folder).mkdirs()
        File(File(root, folder), ".gpg-id").writeText(contents)
    }
}
