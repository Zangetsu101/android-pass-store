// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyProvider
import com.zangetsu101.pass.passstore.PassEntry
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.bouncycastle.openpgp.api.OpenPGPKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.pgpainless.PGPainless
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

@ExtendWith(MockKExtension::class)
class DecryptionImplTest {
    private lateinit var testKey: OpenPGPKey
    private lateinit var gpgKeyProvider: GpgKeyProvider
    private lateinit var decryption: DecryptionImpl
    private lateinit var tmpDir: File

    @BeforeEach
    fun setup() {
        testKey =
            PGPainless()
                .generateKey()
                .modernKeyRing("Test User <test@example.com>")

        gpgKeyProvider = mockk()
        decryption = DecryptionImpl(gpgKeyProvider)
        tmpDir = Files.createTempDirectory("decrypt_test").toFile()
    }

    private fun encryptText(plaintext: String): ByteArray {
        val output = ByteArrayOutputStream()
        val encStream =
            PGPainless()
                .generateMessage()
                .onOutputStream(output)
                .withOptions(
                    ProducerOptions.encrypt(
                        EncryptionOptions
                            .encryptCommunications()
                            .addRecipient(testKey),
                    ),
                )
        encStream.use { it.write(plaintext.toByteArray()) }
        return output.toByteArray()
    }

    private fun makeEntry(
        filename: String,
        content: String,
    ): PassEntry {
        val file = File(tmpDir, filename)
        file.writeBytes(encryptText(content))
        return PassEntry(
            path = filename,
            domain = "example.com",
            username = filename.removeSuffix(".gpg"),
            encryptedFile = file,
        )
    }

    @Test
    fun `decrypt returns password from line 1 of single-line file`() =
        runTest {
            coEvery { gpgKeyProvider.getGpgKey(any()) } returns testKey
            val entry = makeEntry("alice.gpg", "s3cr3t")
            val activity: FragmentActivity = mockk()

            val creds = decryption.decrypt(entry, activity)

            assertArrayEquals("s3cr3t".toCharArray(), creds.password)
        }

    @Test
    fun `decrypt returns password and notes from multi-line file`() =
        runTest {
            coEvery { gpgKeyProvider.getGpgKey(any()) } returns testKey
            val entry = makeEntry("bob.gpg", "password123\nusername: bob\nurl: https://example.com")
            val activity: FragmentActivity = mockk()

            val creds = decryption.decrypt(entry, activity)

            assertArrayEquals("password123".toCharArray(), creds.password)
            assertTrue(creds.notes.contains("username: bob"))
        }

    @Test
    fun `decrypt username comes from filename not file content`() =
        runTest {
            coEvery { gpgKeyProvider.getGpgKey(any()) } returns testKey
            val entry = makeEntry("carol.gpg", "autofillpass\nnotes: blah")
            val activity: FragmentActivity = mockk()

            val creds = decryption.decrypt(entry, activity)

            assertArrayEquals("autofillpass".toCharArray(), creds.password)
            assertEquals("carol", creds.username)
        }

    @Test
    fun `decrypt throws DecryptionError for wrong key`() =
        runTest {
            val wrongKey =
                PGPainless()
                    .generateKey()
                    .modernKeyRing("Wrong <wrong@example.com>")
            coEvery { gpgKeyProvider.getGpgKey(any()) } returns wrongKey
            val entry = makeEntry("alice.gpg", "secret")
            val activity: FragmentActivity = mockk()

            var threw: DecryptionError? = null
            try {
                decryption.decrypt(entry, activity)
            } catch (e: DecryptionError) {
                threw = e
            }
            assertNotNull(threw, "Expected DecryptionError")
        }

    @Test
    fun `decrypt throws DecryptionError for corrupted file`() =
        runTest {
            coEvery { gpgKeyProvider.getGpgKey(any()) } returns testKey
            val file = File(tmpDir, "corrupt.gpg").also { it.writeText("not valid gpg content") }
            val entry = PassEntry("corrupt.gpg", "example.com", "corrupt", file)
            val activity: FragmentActivity = mockk()

            var threw: DecryptionError? = null
            try {
                decryption.decrypt(entry, activity)
            } catch (e: DecryptionError) {
                threw = e
            }
            assertNotNull(threw, "Expected DecryptionError")
        }

    @Test
    fun `Credentials password wiped after zero() call`() {
        val creds = Credentials("secretpassword".toCharArray(), "notes", "user")
        creds.zero()
        assertTrue(creds.password.all { it == ' ' })
    }
}
