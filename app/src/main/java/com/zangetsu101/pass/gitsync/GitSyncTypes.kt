// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.gitsync

import java.time.Instant

data class FileCommitInfo(
    val commitHash: String,
    val commitTime: Instant,
)

data class SyncResult(
    val newEntries: List<String>,
    val removedEntries: List<String>,
    val lastSyncTime: Instant,
)

data class SyncStatus(
    val lastSyncTime: Instant? = null,
    val localCommit: String? = null,
    val upstreamCommit: String? = null,
    val unpushed: List<UnpushedCommit> = emptyList(),
    val discardPaths: List<EntryChange> = emptyList(),
)

data class UnpushedCommit(
    val message: String,
    val paths: List<String>,
)

data class EntryChange(
    val path: String,
    val action: String,
)

data class SyncState(
    val status: SyncStatus = SyncStatus(),
    val command: String? = null,
    val error: String? = null,
    val conflict: Boolean = false,
    val completed: Boolean = false,
)

sealed class SyncError : Exception() {
    data class EditRejected(
        override val message: String,
    ) : SyncError()

    data class Conflict(
        val paths: List<String>,
    ) : SyncError() {
        override val message: String get() = "Sync conflict · ${paths.joinToString()}"
    }

    data class RecoveryFailure(
        override val message: String,
    ) : SyncError()

    data class RemoteUnreachable(
        override val message: String = "Couldn't reach the remote. Check your connection and try again.",
    ) : SyncError()

    data class AuthFailure(
        override val message: String = "SSH authentication failed. Check that your key has access to the remote.",
    ) : SyncError()

    data class CloneFailure(
        override val cause: Throwable,
    ) : SyncError() {
        override val message: String get() = "Clone failed: ${cause.message}"
    }
}
