package com.example.pass.gitsync

import org.eclipse.jgit.lib.ProgressMonitor
import java.nio.file.Path
import java.security.KeyPair

interface GitSync {
    suspend fun clone(
        remoteUrl: String,
        localPath: Path,
        sshKeyPair: KeyPair? = null,
        progressMonitor: ProgressMonitor? = null,
    )

    suspend fun pull(sshKeyPair: KeyPair? = null): SyncResult

    suspend fun syncStatus(): SyncStatus

    suspend fun lastCommitForFile(repoRelativePath: String): FileCommitInfo?
}
