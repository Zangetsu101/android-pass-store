package com.example.pass.gitsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.TransportConfigCallback
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.nio.file.Path
import java.security.KeyPair
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gitSyncDataStore by preferencesDataStore("git_sync")
private val KEY_LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
private val KEY_REMOTE_URL = stringPreferencesKey("remote_url")
private val KEY_REPO_PATH = stringPreferencesKey("repo_path")

@Singleton
class GitSyncImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : GitSync {

    private val sshTmpDir get() = File(context.filesDir, "tmp_ssh")

    private suspend fun repoDir(): File {
        val path = context.gitSyncDataStore.data.first()[KEY_REPO_PATH]
        return if (path != null) File(path) else File(context.filesDir, "repo")
    }

    override suspend fun clone(remoteUrl: String, localPath: Path, sshKeyPair: KeyPair?) {
        withContext(Dispatchers.IO) {
            try {
                Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(localPath.toFile())
                    .setTransportConfigCallback(transportCallback(sshKeyPair))
                    .call()
                    .close()
                context.gitSyncDataStore.edit {
                    it[KEY_REMOTE_URL] = remoteUrl
                    it[KEY_REPO_PATH] = localPath.toAbsolutePath().toString()
                    it[KEY_LAST_SYNC] = System.currentTimeMillis()
                }
            } catch (e: TransportException) {
                val msg = e.message ?: ""
                if (msg.contains("Auth fail") || msg.contains("authentication")) {
                    throw SyncError.AuthFailure()
                }
                throw SyncError.CloneFailure(e)
            } catch (e: Exception) {
                throw SyncError.CloneFailure(e)
            } finally {
                cleanupSshTmp()
            }
        }
    }

    override suspend fun pull(sshKeyPair: KeyPair?): SyncResult = withContext(Dispatchers.IO) {
        val git = openRepo(repoDir())
        val beforeHead: ObjectId = git.repository.resolve("HEAD")
            ?: error("Repository has no HEAD")

        try {
            val result = git.pull()
                .setFastForward(org.eclipse.jgit.api.MergeCommand.FastForwardMode.FF_ONLY)
                .setTransportConfigCallback(transportCallback(sshKeyPair))
                .call()

            if (!result.isSuccessful) {
                throw SyncError.NotFastForward()
            }

            val afterHead: ObjectId = git.repository.resolve("HEAD")!!
            val syncTime = Instant.now()
            context.gitSyncDataStore.edit { it[KEY_LAST_SYNC] = syncTime.toEpochMilli() }

            val diff = diffEntries(git, beforeHead, afterHead)
            SyncResult(
                newEntries = diff.filter { it.changeType != DiffEntry.ChangeType.DELETE }
                    .map { it.newPath }.filter { it.endsWith(".gpg") },
                removedEntries = diff.filter { it.changeType == DiffEntry.ChangeType.DELETE }
                    .map { it.oldPath }.filter { it.endsWith(".gpg") },
                lastSyncTime = syncTime,
            )
        } catch (e: SyncError) {
            throw e
        } catch (e: TransportException) {
            val msg = e.message ?: ""
            when {
                msg.contains("Auth fail") || msg.contains("authentication") -> throw SyncError.AuthFailure()
                msg.contains("not reachable") || msg.contains("Connection") -> throw SyncError.RemoteUnreachable()
                msg.contains("non-fast") || msg.contains("rejected") -> throw SyncError.NotFastForward()
                else -> throw SyncError.RemoteUnreachable()
            }
        } finally {
            git.close()
            cleanupSshTmp()
        }
    }

    override suspend fun syncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        val prefs = context.gitSyncDataStore.data.first()
        val lastSyncMs = prefs[KEY_LAST_SYNC]
        val remoteUrl = prefs[KEY_REMOTE_URL]

        val currentRepoDir = repoDir()
        val localCommit: String? = if (currentRepoDir.exists()) {
            runCatching {
                val git = openRepo(currentRepoDir)
                val id = git.repository.resolve("HEAD")?.name
                git.close()
                id
            }.getOrNull()
        } else null

        val remoteReachable = if (remoteUrl != null && currentRepoDir.exists()) {
            runCatching {
                val git = openRepo(currentRepoDir)
                git.fetch().setDryRun(true).call()
                git.close()
                true
            }.getOrElse { false }
        } else {
            false
        }

        SyncStatus(
            lastSyncTime = lastSyncMs?.let { Instant.ofEpochMilli(it) },
            localCommit = localCommit,
            remoteReachable = remoteReachable,
        )
    }

    private fun openRepo(dir: File): Git = Git.open(dir)

    private fun transportCallback(keyPair: KeyPair?): TransportConfigCallback =
        TransportConfigCallback { transport ->
            if (transport is SshTransport && keyPair != null) {
                transport.sshSessionFactory = buildSshdFactory(keyPair)
            }
        }

    private fun buildSshdFactory(keyPair: KeyPair): SshdSessionFactory {
        sshTmpDir.mkdirs()
        val keyFile = File(sshTmpDir, "id_ed25519")
        keyFile.outputStream().use { out ->
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, null, null, out)
        }
        // SshdSessionFactory resolves identities from the ssh home directory
        return object : SshdSessionFactory(null, null) {
            override fun getDefaultIdentities(sshDir: File): MutableList<Path> =
                mutableListOf(keyFile.toPath())

            override fun createDefaultKnownHosts(sshDir: File?): File? = null
        }
    }

    private fun cleanupSshTmp() {
        sshTmpDir.listFiles()?.forEach { it.delete() }
    }

    private fun diffEntries(git: Git, before: ObjectId, after: ObjectId): List<DiffEntry> {
        if (before == after) return emptyList()
        val reader = git.repository.newObjectReader()
        return try {
            val oldTree = org.eclipse.jgit.treewalk.CanonicalTreeParser().apply {
                reset(reader, git.repository.parseCommit(before).tree)
            }
            val newTree = org.eclipse.jgit.treewalk.CanonicalTreeParser().apply {
                reset(reader, git.repository.parseCommit(after).tree)
            }
            git.diff().setOldTree(oldTree).setNewTree(newTree).call()
        } finally {
            reader.close()
        }
    }
}
