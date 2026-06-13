package com.zangetsu101.pass.gitsync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.After
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
    private lateinit var bareRepo: File
    private lateinit var context: Context
    private lateinit var gitSync: GitSyncImpl
    private lateinit var repoDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
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
        bareRepo.deleteRecursively()
        repoDir.deleteRecursively()
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
    fun `pull returns NotFastForward when remote diverged`() =
        runTest {
            gitSync.clone(bareRepo.toURI().toString(), repoDir.toPath())

            // Diverge local repo
            val localGit = Git.open(repoDir)
            File(repoDir, "local_only.gpg").writeText("local")
            localGit.add().addFilepattern(".").call()
            localGit
                .commit()
                .setMessage("local diverge")
                .setAuthor("test", "test@test.com")
                .call()
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
