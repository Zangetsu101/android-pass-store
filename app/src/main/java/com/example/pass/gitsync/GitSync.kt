package com.example.pass.gitsync

import com.example.pass.keymanagement.SshKeyPair
import org.eclipse.jgit.lib.ProgressMonitor
import java.nio.file.Path

interface GitSync {
    suspend fun clone(
        remoteUrl: String,
        localPath: Path,
        sshKeyPair: SshKeyPair? = null,
        progressMonitor: ProgressMonitor? = null,
    )

    suspend fun pull(sshKeyPair: SshKeyPair? = null): SyncResult

    suspend fun syncStatus(): SyncStatus

    suspend fun lastCommitForFile(repoRelativePath: String): FileCommitInfo?
}
