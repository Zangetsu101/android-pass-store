// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.gitsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zangetsu101.pass.keymanagement.SshKeyPair
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.digest.BuiltinDigests
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectInserter
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.Security
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val GITHUB_HOST_FINGERPRINTS =
    setOf(
        "SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU", // ed25519
        "SHA256:p2QAMXNIC1TJYWeIOttrVc98/R1BUFWu3/LiyKgUfQM", // ecdsa-nistp256
        "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s", // rsa
    )

internal val Context.gitSyncDataStore by preferencesDataStore("git_sync")
private val KEY_LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
private val KEY_REMOTE_URL = stringPreferencesKey("remote_url")
private val KEY_REPO_PATH = stringPreferencesKey("repo_path")

internal suspend fun Context.repositoryDirectory(): File {
    val path = gitSyncDataStore.data.first()[KEY_REPO_PATH]
    return if (path != null) File(path) else File(filesDir, "repo")
}

@Singleton
class GitSyncImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sshKeyStore: SshKeyStore,
    ) : GitSync {
        private val operations = Mutex()

        init {
            // MINA SSHD's PathUtils rejects Android's empty "user.home". Set a valid path.
            System.setProperty("user.home", context.filesDir.absolutePath)
            // Android ships a stripped "BC" provider missing X25519/ECDH. Replace it with the
            // full Bouncy Castle so MINA SSHD's key-exchange factories resolve their JCE algorithms.
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }

        override suspend fun clone(
            remoteUrl: String,
            localPath: Path,
            sshKeyPair: SshKeyPair?,
            progressMonitor: ProgressMonitor?,
        ) {
            withRepositoryLock {
                try {
                    val cmd =
                        Git
                            .cloneRepository()
                            .setURI(remoteUrl)
                            .setDirectory(localPath.toFile())
                    if (sshKeyPair != null) {
                        cmd.setTransportConfigCallback(makeSshCallback(sshKeyPair))
                    }
                    if (progressMonitor != null) {
                        cmd.setProgressMonitor(progressMonitor)
                    }
                    cmd.call().close()
                    context.gitSyncDataStore.edit {
                        it[KEY_REMOTE_URL] = remoteUrl
                        it[KEY_REPO_PATH] = localPath.toAbsolutePath().toString()
                        it[KEY_LAST_SYNC] = System.currentTimeMillis()
                    }
                } catch (e: TransportException) {
                    val msg = e.message ?: ""
                    when {
                        msg.contains("Auth fail") || msg.contains("authentication") -> throw SyncError.AuthFailure()
                        else -> throw SyncError.CloneFailure(e)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw SyncError.CloneFailure(e)
                }
            }
        }

        override suspend fun pull(): SyncResult =
            withRepositoryLock {
                val keyPair = remoteSshKey()

                val git = openRepo(context.repositoryDirectory())
                val beforeHead: ObjectId =
                    git.repository.resolve("HEAD")
                        ?: error("Repository has no HEAD")

                try {
                    val cmd =
                        git
                            .pull()
                            .setRebase(false)
                            .setFastForward(FastForwardMode.FF_ONLY)
                    if (keyPair != null) {
                        cmd.setTransportConfigCallback(makeSshCallback(keyPair))
                    }
                    val result = cmd.call()

                    if (!result.isSuccessful) throw SyncError.NotFastForward()

                    val afterHead: ObjectId = git.repository.resolve("HEAD")!!
                    val syncTime = Instant.now()
                    context.gitSyncDataStore.edit { it[KEY_LAST_SYNC] = syncTime.toEpochMilli() }

                    val diff = diffEntries(git, beforeHead, afterHead)
                    SyncResult(
                        newEntries =
                            diff
                                .filter { it.changeType != DiffEntry.ChangeType.DELETE }
                                .map { it.newPath }
                                .filter { it.endsWith(".gpg") },
                        removedEntries =
                            diff
                                .filter { it.changeType == DiffEntry.ChangeType.DELETE }
                                .map { it.oldPath }
                                .filter { it.endsWith(".gpg") },
                        lastSyncTime = syncTime,
                    )
                } catch (e: SyncError) {
                    throw e
                } catch (e: TransportException) {
                    val msg = e.message ?: ""
                    when {
                        msg.contains("Auth fail") || msg.contains("authentication") -> throw SyncError.AuthFailure()
                        msg.contains("non-fast") || msg.contains("rejected") -> throw SyncError.NotFastForward()
                        else -> throw SyncError.RemoteUnreachable()
                    }
                } finally {
                    git.close()
                }
            }

        override suspend fun syncStatus(): SyncStatus =
            withContext(Dispatchers.IO) {
                val prefs = context.gitSyncDataStore.data.first()
                val lastSyncMs = prefs[KEY_LAST_SYNC]
                val remoteUrl = prefs[KEY_REMOTE_URL]
                val currentRepoDir = context.repositoryDirectory()

                val localCommit: String? =
                    if (currentRepoDir.exists()) {
                        runCatching {
                            openRepo(currentRepoDir).use { it.repository.resolve("HEAD")?.name }
                        }.getOrNull()
                    } else {
                        null
                    }

                val remoteReachable =
                    if (remoteUrl != null && currentRepoDir.exists()) {
                        runCatching {
                            openRepo(currentRepoDir).use { git ->
                                git.fetch().setDryRun(true).call()
                                true
                            }
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

        override suspend fun commitEdit(
            path: String,
            prepare: (root: File, originalCiphertext: ByteArray) -> EncryptedEdit,
        ): FileCommitInfo =
            withRepositoryLock {
                val root = context.repositoryDirectory().canonicalFile
                val file = File(root, path)
                if (!path.endsWith(".gpg") || file != file.canonicalFile || !file.toPath().startsWith(root.toPath())) {
                    throw SyncError.EditRejected("invalid entry path")
                }
                openRepo(root).use { git ->
                    if (git.repository.repositoryState != RepositoryState.SAFE) {
                        throw SyncError.EditRejected("repository is busy")
                    }
                    val indexEntry = git.repository.readDirCache().getEntry(path)
                    if (indexEntry == null || !file.isFile) {
                        throw SyncError.EditRejected("entry no longer exists")
                    }
                    val before = git.repository.resolve(Constants.HEAD)
                    val original = file.readBytes()
                    val edit = prepare(root, original)
                    withContext(NonCancellable) {
                        replaceFile(file, edit.ciphertext)
                        val commit =
                            try {
                                // setOnly stages this path and leaves other staged files out of the commit.
                                git
                                    .commit()
                                    .setOnly(path)
                                    .setAuthor(edit.author)
                                    .setCommitter(edit.author)
                                    .setSign(false)
                                    .setMessage("Edit password for ${path.removeSuffix(".gpg")} using editor.")
                                    .call()
                            } catch (failure: Exception) {
                                val after = git.repository.resolve(Constants.HEAD)
                                val editedBlob = ObjectInserter.Formatter().use { it.idFor(Constants.OBJ_BLOB, edit.ciphertext) }
                                if (after != before && git.repository.resolve("HEAD:$path") == editedBlob) {
                                    // A post-commit error can occur after HEAD has already advanced.
                                    git.repository.parseCommit(after)
                                } else {
                                    try {
                                        replaceFile(file, original)
                                        restoreIndexEntry(git.repository, indexEntry)
                                    } catch (rollbackFailure: Exception) {
                                        failure.addSuppressed(rollbackFailure)
                                    }
                                    throw failure
                                }
                            }
                        FileCommitInfo(commit.name.take(7), Instant.ofEpochSecond(commit.commitTime.toLong()))
                    }
                }
            }

        override suspend fun push() {
            withRepositoryLock {
                openRepo(context.repositoryDirectory()).use { git ->
                    val branch = git.repository.fullBranch
                    check(branch.startsWith("refs/heads/")) { "repository has no checked-out branch" }
                    val branchName = branch.removePrefix("refs/heads/")
                    val config = git.repository.config
                    val remote = config.getString("branch", branchName, "remote") ?: "origin"
                    val destination = config.getString("branch", branchName, "merge") ?: branch
                    val command =
                        git
                            .push()
                            .setRemote(remote)
                            .setRefSpecs(RefSpec("$branch:$destination"))
                            .setTimeout(30)
                    remoteSshKey()?.let { command.setTransportConfigCallback(makeSshCallback(it)) }
                    val updates = command.call().flatMap { it.remoteUpdates }
                    check(
                        updates.isNotEmpty() &&
                            updates.all {
                                it.status == RemoteRefUpdate.Status.OK || it.status == RemoteRefUpdate.Status.UP_TO_DATE
                            },
                    ) { "push rejected" }
                }
            }
        }

        private suspend fun remoteSshKey(): SshKeyPair? {
            val url = context.gitSyncDataStore.data.first()[KEY_REMOTE_URL]
            val isSsh = url?.startsWith("git@") == true || url?.startsWith("ssh://") == true
            return if (isSsh && sshKeyStore.exists()) sshKeyStore.getSshKey() else null
        }

        private suspend fun <T> withRepositoryLock(block: suspend () -> T): T =
            withContext(Dispatchers.IO) { operations.withLock { block() } }

        private fun restoreIndexEntry(
            repository: Repository,
            original: DirCacheEntry,
        ) {
            val index = repository.lockDirCache()
            try {
                val position = index.findEntry(original.pathString)
                check(position >= 0)
                val builder = index.builder()
                builder.keep(0, position)
                builder.add(original)
                val remaining = index.entryCount - position - 1
                if (remaining > 0) builder.keep(position + 1, remaining)
                check(builder.commit()) { "could not restore index entry" }
            } finally {
                index.unlock()
            }
        }

        private fun replaceFile(
            file: File,
            content: ByteArray,
        ) {
            val temporary = Files.createTempFile(file.parentFile!!.toPath(), ".pass-", ".tmp")
            try {
                Files.write(temporary, content)
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

        private fun makeSshCallback(keyPair: SshKeyPair): TransportConfigCallback {
            val sshDir = File(context.cacheDir, "ssh")
            val factory =
                object : SshdSessionFactory(null, null) {
                    override fun getSshDirectory(): File = sshDir

                    override fun getDefaultKeys(sshDir: File): List<SshKeyPair> = listOf(keyPair)

                    override fun getDefaultPreferredAuthentications(): String = "publickey"

                    override fun getServerKeyDatabase(
                        homeDir: File,
                        sshDir: File,
                    ): ServerKeyDatabase =
                        object : ServerKeyDatabase {
                            override fun lookup(
                                connectAddress: String,
                                remoteAddress: java.net.InetSocketAddress,
                                config: ServerKeyDatabase.Configuration,
                            ): List<java.security.PublicKey> = emptyList()

                            override fun accept(
                                connectAddress: String,
                                remoteAddress: java.net.InetSocketAddress,
                                serverKey: java.security.PublicKey,
                                config: ServerKeyDatabase.Configuration,
                                provider: org.eclipse.jgit.transport.CredentialsProvider?,
                            ): Boolean {
                                val fp = KeyUtils.getFingerPrint(BuiltinDigests.sha256, serverKey)
                                return fp in GITHUB_HOST_FINGERPRINTS
                            }
                        }
                }
            return TransportConfigCallback { transport ->
                if (transport is SshTransport) {
                    transport.sshSessionFactory = factory
                }
            }
        }

        override suspend fun lastCommitForFile(repoRelativePath: String): FileCommitInfo? =
            withContext(Dispatchers.IO) {
                val dir = context.repositoryDirectory()
                if (!dir.exists()) return@withContext null
                runCatching {
                    openRepo(dir).use { git ->
                        val commits =
                            git
                                .log()
                                .addPath(repoRelativePath)
                                .setMaxCount(1)
                                .call()
                        val commit =
                            commits.iterator().let { if (it.hasNext()) it.next() else null }
                                ?: return@withContext null
                        FileCommitInfo(
                            commitHash = commit.name.take(7),
                            commitTime = Instant.ofEpochSecond(commit.commitTime.toLong()),
                        )
                    }
                }.getOrNull()
            }

        private fun openRepo(dir: File): Git = Git.open(dir)

        private fun diffEntries(
            git: Git,
            before: ObjectId,
            after: ObjectId,
        ): List<DiffEntry> {
            if (before == after) return emptyList()
            val reader = git.repository.newObjectReader()
            return try {
                val oldTree =
                    CanonicalTreeParser().apply {
                        reset(reader, git.repository.parseCommit(before).tree)
                    }
                val newTree =
                    CanonicalTreeParser().apply {
                        reset(reader, git.repository.parseCommit(after).tree)
                    }
                git
                    .diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .call()
            } finally {
                reader.close()
            }
        }
    }
