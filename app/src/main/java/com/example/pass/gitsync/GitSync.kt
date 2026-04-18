package com.example.pass.gitsync

import java.nio.file.Path
import java.security.KeyPair

interface GitSync {
    suspend fun clone(remoteUrl: String, localPath: Path, sshKeyPair: KeyPair? = null)
    suspend fun pull(sshKeyPair: KeyPair? = null): SyncResult
    suspend fun syncStatus(): SyncStatus
}
