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
    val lastSyncTime: Instant?,
    val localCommit: String?,
    val remoteReachable: Boolean,
)

sealed class SyncError : Exception() {
    data class NotFastForward(
        override val message: String = "Remote diverged; cannot fast-forward",
    ) : SyncError()

    data class RemoteUnreachable(
        override val message: String = "Remote unreachable",
    ) : SyncError()

    data class AuthFailure(
        override val message: String = "SSH authentication failed",
    ) : SyncError()

    data class CloneFailure(
        override val cause: Throwable,
    ) : SyncError() {
        override val message: String get() = "Clone failed: ${cause.message}"
    }
}
