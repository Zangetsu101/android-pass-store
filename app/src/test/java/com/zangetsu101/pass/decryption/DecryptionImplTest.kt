package com.zangetsu101.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.keymanagement.CryptoOperations
import com.zangetsu101.pass.passstore.PassEntry
import kotlinx.coroutines.test.runTest
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.DocumentSignatureType
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

@RunWith(JUnit4::class)
class DecryptionImplTest {
    private lateinit var testKey: PGPSecretKeyRing
    private lateinit var keyManagement: CryptoOperations
    private lateinit var decryption: DecryptionImpl
    private lateinit var tmpDir: File

    @Before
    fun setup() {
        // Generate a throwaway key for testing
        testKey =
            PGPainless
                .generateKeyRing()
                .modernKeyRing("Test User <test@example.com>")

        keyManagement = mock()
        decryption = DecryptionImpl(keyManagement)
        tmpDir = Files.createTempDirectory("decrypt_test").toFile()
    }

    private fun encryptText(plaintext: String): ByteArray {
        val publicKey = PGPainless.extractCertificate(testKey)
        val output = ByteArrayOutputStream()
        val encStream =
            PGPainless
                .encryptAndOrSign()
                .onOutputStream(output)
                .withOptions(
                    ProducerOptions.encrypt(
                        EncryptionOptions
                            .encryptCommunications()
                            .addRecipient(publicKey),
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
            whenever(keyManagement.getGpgKey(any())).thenReturn(testKey)
            val entry = makeEntry("alice.gpg", "s3cr3t")
            val activity: FragmentActivity = mock()

            val creds = decryption.decrypt(entry, activity)

            assertArrayEquals("s3cr3t".toCharArray(), creds.password)
        }

    @Test
    fun `decrypt returns password and notes from multi-line file`() =
        runTest {
            whenever(keyManagement.getGpgKey(any())).thenReturn(testKey)
            val entry = makeEntry("bob.gpg", "password123\nusername: bob\nurl: https://example.com")
            val activity: FragmentActivity = mock()

            val creds = decryption.decrypt(entry, activity)

            assertArrayEquals("password123".toCharArray(), creds.password)
            assertTrue(creds.notes.contains("username: bob"))
        }

    @Test
    fun `decrypt throws DecryptionError for wrong key`() =
        runTest {
            val wrongKey =
                PGPainless
                    .generateKeyRing()
                    .modernKeyRing("Wrong <wrong@example.com>")
            whenever(keyManagement.getGpgKey(any())).thenReturn(wrongKey)
            val entry = makeEntry("alice.gpg", "secret")
            val activity: FragmentActivity = mock()

            var threw: DecryptionError? = null
            try {
                decryption.decrypt(entry, activity)
            } catch (e: DecryptionError) {
                threw = e
            }
            assertNotNull("Expected DecryptionError", threw)
        }

    @Test
    fun `decrypt throws DecryptionError for corrupted file`() =
        runTest {
            whenever(keyManagement.getGpgKey(any())).thenReturn(testKey)
            val file = File(tmpDir, "corrupt.gpg").also { it.writeText("not valid gpg content") }
            val entry = PassEntry("corrupt.gpg", "example.com", "corrupt", file)
            val activity: FragmentActivity = mock()

            var threw: DecryptionError? = null
            try {
                decryption.decrypt(entry, activity)
            } catch (e: DecryptionError) {
                threw = e
            }
            assertNotNull("Expected DecryptionError", threw)
        }

    @Test
    fun `decryptForAutofill returns only password without notes`() =
        runTest {
            whenever(keyManagement.getGpgKey(any())).thenReturn(testKey)
            val entry = makeEntry("carol.gpg", "autofillpass\nnotes: blah")
            val activity: FragmentActivity = mock()

            val creds = decryption.decryptForAutofill(entry, activity)

            assertArrayEquals("autofillpass".toCharArray(), creds.password)
            assertEquals("carol", creds.username)
        }

    @Test
    fun `CharArray is all-zero after zero() call`() {
        val arr = "secretpassword".toCharArray()
        arr.zero()
        assertTrue(arr.all { it == '\u0000' })
    }
}
