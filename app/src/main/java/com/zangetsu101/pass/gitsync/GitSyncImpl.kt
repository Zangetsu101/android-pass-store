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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.digest.BuiltinDigests
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand.ResetType
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
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
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
        private val state = MutableStateFlow(SyncState())
        override val syncState = state.asStateFlow()

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
                    openRepo(localPath.toFile()).use { state.value = SyncState(readStatus(it)) }
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
                state.update { it.copy(command = "git fetch", error = null, conflict = false, completed = false) }
                try {
                    openRepo(context.repositoryDirectory()).use { git ->
                        try {
                            syncRepository(git)
                        } finally {
                            withContext(NonCancellable) {
                                val status = readStatus(git)
                                state.update { it.copy(status = status) }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val error = syncError(failure, state.value.command ?: "sync")
                    state.update { it.copy(error = error.message, conflict = error is SyncError.Conflict, completed = false) }
                    throw error
                } finally {
                    state.update { it.copy(command = null) }
                }
            }

        private suspend fun syncRepository(git: Git): SyncResult {
            val repo = git.repository
            val before = requireNotNull(repo.resolve(Constants.HEAD))
            val remote = remoteBranch(git)
            val callback = remoteSshKey()?.let(::makeSshCallback)
            state.update { it.copy(command = "git fetch ${remote.name}") }
            val fetch =
                git
                    .fetch()
                    .setRemote(remote.name)
                    .setTimeout(30)
                    .setRemoveDeletedRefs(true)
            callback?.let(fetch::setTransportConfigCallback)
            fetch.call()

            val upstream =
                repo.resolve(remote.trackingRef)
                    ?: throw SyncError.RecoveryFailure("The tracked branch is missing. Check the remote branch.")
            if (before != upstream) {
                val base =
                    mergeBase(repo, before, upstream)
                        ?: throw SyncError.RecoveryFailure("The remote history was replaced. Check the remote branch before syncing.")
                if (base != upstream) {
                    if (base != before) {
                        val localPaths = commitChanges(git, upstream, before).flatMap { it.paths }.toSet()
                        val remotePaths = commitChanges(git, before, upstream).flatMap { it.paths }.toSet()
                        val conflicts = localPaths.intersect(remotePaths).sorted()
                        if (conflicts.isNotEmpty()) throw SyncError.Conflict(conflicts)
                    }
                    state.update { it.copy(command = "git rebase ${remote.trackingRef.removePrefix(Constants.R_REMOTES)}") }
                    rebase(git, upstream)
                }
            }
            val after = repo.resolve(Constants.HEAD)
            if (after != upstream) {
                state.update { it.copy(command = "git push ${remote.name}") }
                pushBranch(git, remote, callback)
            }
            val lastSync = recordSyncTime()
            val diff = diffEntries(git, before, after)
            state.update { it.copy(completed = true) }
            return SyncResult(
                newEntries = diff.map { it.newPath }.filter { it.endsWith(".gpg") },
                removedEntries =
                    diff
                        .filter { it.changeType == DiffEntry.ChangeType.DELETE }
                        .map { it.oldPath }
                        .filter { it.endsWith(".gpg") },
                lastSyncTime = lastSync,
            )
        }

        override suspend fun syncStatus(): SyncStatus =
            withRepositoryLock {
                val root = context.repositoryDirectory()
                val status = if (File(root, ".git").exists()) openRepo(root).use { readStatus(it) } else SyncStatus()
                state.update { it.copy(status = status) }
                status
            }

        override suspend fun discardUnpushed(expected: SyncStatus) {
            withRepositoryLock {
                openRepo(context.repositoryDirectory()).use { git ->
                    val current = readStatus(git)
                    if (
                        !state.value.conflict || current != expected || current.upstreamCommit == null ||
                        current.unpushed.isEmpty() || git.repository.repositoryState != RepositoryState.SAFE ||
                        !git.status().call().isClean
                    ) {
                        throw SyncError.RecoveryFailure("The working copy changed. Return home and sync again before discarding.")
                    }
                    withContext(NonCancellable) {
                        git
                            .reset()
                            .setMode(ResetType.HARD)
                            .setRef(current.upstreamCommit)
                            .call()
                        recordSyncTime()
                        state.value = SyncState(status = readStatus(git), completed = true)
                    }
                }
            }
        }

        private fun rebase(
            git: Git,
            upstream: ObjectId,
        ) {
            if (git.repository.repositoryState != RepositoryState.SAFE || !git.status().call().isClean) {
                throw SyncError.RecoveryFailure("The working copy has unfinished changes. Resolve them before syncing.")
            }
            try {
                val result = git.rebase().setUpstream(upstream).call()
                if (!result.status.isSuccessful) {
                    val conflicts =
                        git
                            .status()
                            .call()
                            .conflicting
                            .map { it.removeSuffix(".gpg") }
                            .sorted()
                    if (conflicts.isNotEmpty()) throw SyncError.Conflict(conflicts)
                    throw SyncError.RecoveryFailure("Couldn't rebase the local changes (${result.status}).")
                }
            } finally {
                if (git.repository.repositoryState.isRebasing) {
                    check(
                        git
                            .rebase()
                            .setOperation(RebaseCommand.Operation.ABORT)
                            .call()
                            .status == RebaseResult.Status.ABORTED,
                    ) {
                        "Couldn't restore the working copy after rebase"
                    }
                }
            }
        }

        private fun syncError(
            failure: Exception,
            command: String,
        ): SyncError =
            when (failure) {
                is SyncError -> {
                    failure
                }

                is TransportException -> {
                    val message = failure.message.orEmpty()
                    when {
                        message.contains("auth", ignoreCase = true) || message.contains("publickey", ignoreCase = true) -> {
                            SyncError.AuthFailure()
                        }

                        command.startsWith("git push") && (
                            message.contains("write access", ignoreCase = true) || message.contains("read only", ignoreCase = true)
                        ) -> {
                            SyncError.RecoveryFailure("Push was rejected. Check that your SSH key has write access and try again.")
                        }

                        else -> {
                            SyncError.RemoteUnreachable()
                        }
                    }
                }

                else -> {
                    SyncError.RecoveryFailure("$command failed. Try syncing again.")
                }
            }

        private suspend fun recordSyncTime(): Instant {
            val now = Instant.now()
            context.gitSyncDataStore.edit { it[KEY_LAST_SYNC] = now.toEpochMilli() }
            return now
        }

        private suspend fun readStatus(git: Git): SyncStatus {
            val repo = git.repository
            val head = repo.resolve(Constants.HEAD)
            val upstream = repo.resolve(remoteBranch(git).trackingRef)
            val lastSync =
                context.gitSyncDataStore.data
                    .first()[KEY_LAST_SYNC]
                    ?.let(Instant::ofEpochMilli)
            if (head == null || upstream == null) return SyncStatus(lastSync, head?.name)
            val unpushed = commitChanges(git, upstream, head)
            val base = if (unpushed.isEmpty()) null else mergeBase(repo, head, upstream)
            val paths =
                base?.let { diffEntries(git, it, head) }.orEmpty().mapNotNull { change ->
                    val deleted = change.changeType == DiffEntry.ChangeType.DELETE
                    val path = if (deleted) change.oldPath else change.newPath
                    if (!path.endsWith(".gpg")) return@mapNotNull null
                    val action =
                        when (change.changeType) {
                            DiffEntry.ChangeType.ADD -> "added"
                            DiffEntry.ChangeType.DELETE -> "deleted"
                            else -> "edited"
                        }
                    EntryChange(path.removeSuffix(".gpg"), action)
                }
            return SyncStatus(lastSync, head.name, upstream.name, unpushed, paths)
        }

        private fun mergeBase(
            repo: Repository,
            head: ObjectId,
            upstream: ObjectId,
        ): ObjectId? =
            RevWalk(repo).use { walk ->
                walk.revFilter = RevFilter.MERGE_BASE
                walk.markStart(walk.parseCommit(head))
                walk.markStart(walk.parseCommit(upstream))
                walk.next()
            }

        private fun commitsBetween(
            git: Git,
            from: ObjectId,
            to: ObjectId,
        ) = if (from == to) {
            emptyList()
        } else {
            git
                .log()
                .addRange(from, to)
                .call()
                .toList()
        }

        private fun commitChanges(
            git: Git,
            from: ObjectId,
            to: ObjectId,
        ): List<UnpushedCommit> =
            commitsBetween(git, from, to).map { commit ->
                val paths =
                    if (commit.parentCount == 0) {
                        TreeWalk(git.repository).use { walk ->
                            walk.addTree(commit.tree)
                            walk.isRecursive = true
                            buildList { while (walk.next()) add(walk.pathString) }
                        }
                    } else {
                        diffEntries(git, commit.getParent(0), commit).flatMap { listOf(it.oldPath, it.newPath) }
                    }
                UnpushedCommit(
                    commit.fullMessage.trimEnd(),
                    paths
                        .filter { it.endsWith(".gpg") }
                        .distinct()
                        .sorted()
                        .map { it.removeSuffix(".gpg") },
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
                        val status = readStatus(git)
                        state.update { it.copy(status = status, completed = false) }
                        FileCommitInfo(commit.name.take(7), Instant.ofEpochSecond(commit.commitTime.toLong()))
                    }
                }
            }

        override suspend fun push() {
            withRepositoryLock {
                openRepo(context.repositoryDirectory()).use { git ->
                    try {
                        val remote = remoteBranch(git)
                        if (git.repository.resolve(remote.trackingRef) != git.repository.resolve(Constants.HEAD)) {
                            pushBranch(git, remote, remoteSshKey()?.let(::makeSshCallback))
                            recordSyncTime()
                        }
                    } finally {
                        withContext(NonCancellable) {
                            state.value = SyncState(status = readStatus(git))
                        }
                    }
                }
            }
        }

        private data class RemoteBranch(
            val name: String,
            val destination: String,
        ) {
            val trackingRef = "refs/remotes/$name/${destination.removePrefix("refs/heads/")}"
        }

        private fun remoteBranch(git: Git): RemoteBranch {
            val branch = git.repository.fullBranch
            check(branch.startsWith(Constants.R_HEADS)) { "Repository has no checked-out branch" }
            val branchName = branch.removePrefix(Constants.R_HEADS)
            val config = git.repository.config
            return RemoteBranch(
                config.getString("branch", branchName, "remote") ?: "origin",
                config.getString("branch", branchName, "merge") ?: branch,
            )
        }

        private fun pushBranch(
            git: Git,
            remote: RemoteBranch,
            callback: TransportConfigCallback?,
        ) {
            val command =
                git
                    .push()
                    .setRemote(remote.name)
                    .setRefSpecs(RefSpec("${git.repository.fullBranch}:${remote.destination}"))
                    .setTimeout(30)
            callback?.let(command::setTransportConfigCallback)
            val updates = command.call().flatMap { it.remoteUpdates }
            if (updates.isEmpty() ||
                updates.any {
                    it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
                }
            ) {
                throw SyncError.RecoveryFailure("Push was rejected. Check that your SSH key has write access and try again.")
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
