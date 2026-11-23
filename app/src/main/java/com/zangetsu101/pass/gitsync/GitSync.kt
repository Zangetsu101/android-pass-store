// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.gitsync

import com.zangetsu101.pass.keymanagement.SshKeyPair
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File
import java.nio.file.Path

class EncryptedEdit(
    val ciphertext: ByteArray,
    val author: PersonIdent,
)

interface GitSync {
    val syncState: StateFlow<SyncState>

    suspend fun clone(
        remoteUrl: String,
        localPath: Path,
        sshKeyPair: SshKeyPair? = null,
        progressMonitor: ProgressMonitor? = null,
    )

    suspend fun sync(): SyncResult

    suspend fun commitEdit(
        path: String,
        prepare: (root: File, originalCiphertext: ByteArray) -> EncryptedEdit,
    ): FileCommitInfo

    suspend fun push()

    suspend fun syncStatus(): SyncStatus

    suspend fun discardUnpushed(expected: SyncStatus)

    suspend fun lastCommitForFile(repoRelativePath: String): FileCommitInfo?
}
