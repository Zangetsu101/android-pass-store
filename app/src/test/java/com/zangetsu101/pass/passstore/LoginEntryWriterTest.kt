// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import com.zangetsu101.pass.decryption.EditableLogin
import com.zangetsu101.pass.decryption.LoginEditSource
import com.zangetsu101.pass.decryption.ciphertextDigest
import com.zangetsu101.pass.gitsync.EncryptedEdit
import com.zangetsu101.pass.gitsync.FileCommitInfo
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.PasswordSource
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.PublicKeySource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pgpainless.PGPainless
import java.io.File
import java.time.Instant

class LoginEntryWriterTest {
    @TempDir lateinit var root: File

    @Test
    fun `a push failure reports that the committed edit is still saved`() =
        runTest {
            val ring =
                PGPainless
                    .getInstance()
                    .generateKey()
                    .modernKeyRing("Alice Example <alice@example.com>")
                    .pgpSecretKeyRing
            val key =
                PublicKeySource(
                    PGPPublicKeyRing(ring.publicKeys.asSequence().toList()),
                    ring.map { it.publicKey.keyID }.toSet(),
                )
            val keyStore = mockk<GpgKeyStore>()
            every { keyStore.publicKeySource() } returns key
            File(root, ".gpg-id").writeText("alice@example.com")
            val git = mockk<GitSync>()
            val store = mockk<PassStore>()
            val entry = PassEntry("alice.gpg", null, "alice", File(root, "alice.gpg"))
            val original = byteArrayOf(1)
            val source = LoginEditSource(EditableLogin("old password"), original.ciphertextDigest())
            val commit = FileCommitInfo("abc1234", Instant.EPOCH)
            coEvery { git.commitEdit(entry.path, PasswordSource.Given, any()) } coAnswers {
                thirdArg<(File, ByteArray) -> EncryptedEdit>().invoke(root, original)
                commit
            }
            coEvery { git.push() } throws IllegalStateException("read-only remote")
            coEvery { store.buildIndex() } returns listOf(entry)
            val writer = LoginEntryWriter(git, store, keyStore, backgroundScope)

            val saved = writer.save(entry, source, "newpassword", "", PasswordSource.Given)
            withTimeout(1_000) { writer.pushFailures.first() }

            assertEquals(commit, saved.commit)
            assertEquals(
                "newpassword",
                saved.entry.credentials.password
                    .concatToString(),
            )
        }

    @Test
    fun `a draft cannot overwrite an entry changed by a pull`() =
        runTest {
            val git = mockk<GitSync>()
            val entry = PassEntry("alice.gpg", null, "alice", File("repo/alice.gpg"))
            val source = LoginEditSource(EditableLogin("old password"), byteArrayOf(1).ciphertextDigest())
            coEvery { git.commitEdit(entry.path, PasswordSource.Given, any()) } coAnswers {
                thirdArg<(File, ByteArray) -> EncryptedEdit>().invoke(File("repo"), byteArrayOf(2))
                error("The writer should reject the edit before preparing ciphertext")
            }
            val appScope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val writer = LoginEntryWriter(git, mockk(), mockk(), appScope)

                val error = runCatching { writer.save(entry, source, "newpassword", "", PasswordSource.Given) }.exceptionOrNull()

                assertEquals("entry changed on disk · reopen it to edit", error?.message)
                coVerify(exactly = 0) { git.push() }
            } finally {
                appScope.cancel()
            }
        }
}
