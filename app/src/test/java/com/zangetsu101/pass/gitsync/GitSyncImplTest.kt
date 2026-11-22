// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.gitsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import com.zangetsu101.pass.passstore.PassStoreImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class GitSyncImplTest {
    private lateinit var originalSystemReader: SystemReader
    private lateinit var bareRepo: File
    private lateinit var context: Context
    private lateinit var gitSync: GitSyncImpl
    private lateinit var repoDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        originalSystemReader = SystemReader.getInstance()
        val emptyConfig = FileBasedConfig(File(context.cacheDir, "isolated-git-config"), FS.DETECTED)
        SystemReader.setInstance(
            spyk(originalSystemReader) {
                every { userConfig } returns emptyConfig
                every { systemConfig } returns emptyConfig
                every { jGitConfig } returns emptyConfig
            },
        )
        val sshKeyStore = mockk<SshKeyStore>(relaxed = true)
        every { sshKeyStore.exists() } returns false
        gitSync = GitSyncImpl(context, sshKeyStore)
        repoDir = File(context.filesDir, "repo")

        // Bare repo = simulated remote
        bareRepo = Files.createTempDirectory("bare_repo").toFile()
        Git
            .init()
            .setDirectory(bareRepo)
            .setBare(true)
            .call()
            .close()

        // Seed it with an initial commit
        val tempWork = Files.createTempDirectory("seed_work").toFile()
        val seedGit =
            Git
                .cloneRepository()
                .setURI(bareRepo.toURI().toString())
                .setDirectory(tempWork)
                .call()
        File(tempWork, "web/github.com/alice.gpg")
            .also { it.parentFile.mkdirs() }
            .writeText("dummy encrypted content")
        seedGit.add().addFilepattern(".").call()
        seedGit
            .commit()
            .setMessage("initial")
            .setAuthor("test", "test@test.com")
            .call()
        seedGit.push().call()
        seedGit.close()
        tempWork.deleteRecursively()
    }

    @After
    fun teardown() {
        try {
            runBlocking { context.gitSyncDataStore.edit { it.clear() } }
            bareRepo.deleteRecursively()
            repoDir.deleteRecursively()
            File(context.filesDir, "custom-repo").deleteRecursively()
        } finally {
            SystemReader.setInstance(originalSystemReader)
        }
    }

    @Test
    fun `commit and push update only the edited entry with the imported identity`() =
        runTest {
            val customDir = File(context.filesDir, "custom-repo")
            gitSync.clone(bareRepo.toURI().toString(), customDir.toPath())
            val path = "web/github.com/alice.gpg"
            Git.open(customDir).use { git ->
                File(customDir, "unrelated.gpg").writeText("unrelated")
                git.add().addFilepattern("unrelated.gpg").call()
            }

            val commit =
                gitSync.commitEdit(path) { root, original ->
                    assertEquals(customDir, root)
                    assertEquals("dummy encrypted content", original.decodeToString())
                    EncryptedEdit("new ciphertext".toByteArray(), PersonIdent("Alice", "alice@example.com"))
                }
            gitSync.push()

            Git.open(bareRepo).use { git ->
                val head =
                    git
                        .log()
                        .setMaxCount(1)
                        .call()
                        .first()
                assertEquals(commit.commitHash, head.name.take(7))
                assertEquals("Edit password for web/github.com/alice using editor.", head.fullMessage)
                assertEquals("Alice", head.authorIdent.name)
                assertEquals("alice@example.com", head.authorIdent.emailAddress)
                assertEquals(null, head.rawGpgSignature)
                assertEquals(
                    "new ciphertext",
                    git.repository
                        .open(git.repository.resolve("HEAD:$path"))
                        .bytes
                        .decodeToString(),
                )
                assertEquals(null, git.repository.resolve("HEAD:unrelated.gpg"))
            }
            Git.open(customDir).use { assertTrue("unrelated.gpg" in it.status().call().added) }
            val indexedEntry = PassStoreImpl(context).buildIndex().first { it.path == path }
            assertEquals(File(customDir, path), indexedEntry.encryptedFile)
        }

    @Test
    fun `a failed commit restores the entry and its index record`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())
            val path = "web/github.com/alice.gpg"
            Git.open(repoDir).use { git ->
                val originalHead = git.repository.resolve("HEAD")
                val refLock = File(git.repository.directory, "${git.repository.fullBranch}.lock")
                assertTrue(refLock.createNewFile())
                try {
                    val result =
                        runCatching {
                            gitSync.commitEdit(path) { _, _ ->
                                EncryptedEdit("new ciphertext".toByteArray(), PersonIdent("Alice", "alice@example.com"))
                            }
                        }
                    assertTrue(result.isFailure)
                } finally {
                    refLock.delete()
                }
                assertEquals(originalHead, git.repository.resolve("HEAD"))
                assertEquals("dummy encrypted content", File(repoDir, path).readText())
                assertTrue(git.status().call().isClean)
            }
        }

    @Test
    fun `a failed push leaves the edit commit local`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())
            val originalRemoteHead = Git.open(bareRepo).use { it.repository.resolve("HEAD") }
            val commit =
                gitSync.commitEdit("web/github.com/alice.gpg") { _, _ ->
                    EncryptedEdit("new ciphertext".toByteArray(), PersonIdent("Alice", "alice@example.com"))
                }
            Git.open(repoDir).use { git ->
                git.repository.config.apply {
                    setString("remote", "origin", "pushurl", File(context.filesDir, "missing").toURI().toString())
                    save()
                }
            }

            assertTrue(runCatching { gitSync.push() }.isFailure)

            Git.open(repoDir).use {
                assertEquals(
                    commit.commitHash,
                    it.repository
                        .resolve("HEAD")
                        .name
                        .take(7),
                )
            }
            Git.open(bareRepo).use { assertEquals(originalRemoteHead, it.repository.resolve("HEAD")) }
        }

    @Test
    fun `clone creates working copy from file-url bare repo`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())

            assertTrue("Git dir should exist", File(repoDir, ".git").exists())
            assertTrue("Entry should be present", File(repoDir, "web/github.com/alice.gpg").exists())
        }

    @Test
    fun `pull with no changes returns empty SyncResult`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())

            val result = gitSync.pull()

            assertTrue(result.newEntries.isEmpty())
            assertTrue(result.removedEntries.isEmpty())
            assertNotNull(result.lastSyncTime)
        }

    @Test
    fun `pull fast-forwards and returns correct SyncResult`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())

            // Push a new entry to bare repo
            val tempWork = Files.createTempDirectory("push_work").toFile()
            val pushGit =
                Git
                    .cloneRepository()
                    .setURI(bareRepo.toURI().toString())
                    .setDirectory(tempWork)
                    .call()
            File(tempWork, "web/example.com/bob.gpg")
                .also { it.parentFile.mkdirs() }
                .writeText("dummy")
            pushGit.add().addFilepattern(".").call()
            pushGit
                .commit()
                .setMessage("add bob")
                .setAuthor("test", "test@test.com")
                .call()
            pushGit.push().call()
            pushGit.close()
            tempWork.deleteRecursively()

            val result = gitSync.pull()

            assertTrue(result.newEntries.any { it.contains("bob.gpg") })
            assertTrue(result.removedEntries.isEmpty())
        }

    @Test
    fun `pull rejects divergence even when rebase is configured`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())

            // Diverge local repo
            val localGit = Git.open(repoDir)
            localGit.repository.config.apply {
                setBoolean("pull", null, "rebase", true)
                save()
            }
            File(repoDir, "local_only.gpg").writeText("local")
            localGit.add().addFilepattern(".").call()
            localGit
                .commit()
                .setMessage("local diverge")
                .setAuthor("test", "test@test.com")
                .call()
            val localHead = localGit.repository.resolve("HEAD").name
            localGit.close()

            // Push a different commit to remote
            val tempWork = Files.createTempDirectory("diverge_work").toFile()
            val pushGit =
                Git
                    .cloneRepository()
                    .setURI(bareRepo.toURI().toString())
                    .setDirectory(tempWork)
                    .call()
            File(tempWork, "remote_only.gpg").writeText("remote")
            pushGit.add().addFilepattern(".").call()
            pushGit
                .commit()
                .setMessage("remote diverge")
                .setAuthor("test", "test@test.com")
                .call()
            pushGit.push().call()
            pushGit.close()
            tempWork.deleteRecursively()

            var threw: SyncError.NotFastForward? = null
            try {
                gitSync.pull()
            } catch (e: SyncError.NotFastForward) {
                threw = e
            }
            assertNotNull("Expected NotFastForward", threw)
            assertEquals(localHead, gitSync.syncStatus().localCommit)
        }

    @Test
    fun `syncStatus reflects last pull timestamp`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())
            gitSync.pull()

            val status = gitSync.syncStatus()

            assertNotNull(status.lastSyncTime)
        }

    @Test
    fun `syncStatus returns remoteReachable=false for invalid path`() =
        runTest {
            val status = gitSync.syncStatus()

            assertFalse(status.remoteReachable)
        }
}
