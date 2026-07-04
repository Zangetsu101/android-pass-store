// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.gitsync

import com.zangetsu101.pass.keymanagement.SshKeyPair
import org.eclipse.jgit.lib.ProgressMonitor
import java.nio.file.Path

interface GitSync {
    suspend fun clone(
        remoteUrl: String,
        localPath: Path,
        sshKeyPair: SshKeyPair? = null,
        progressMonitor: ProgressMonitor? = null,
    )

    suspend fun pull(): SyncResult

    suspend fun syncStatus(): SyncStatus

    suspend fun lastCommitForFile(repoRelativePath: String): FileCommitInfo?
}
