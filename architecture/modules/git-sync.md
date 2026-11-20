# Git Sync Module

## Implementation

- **Git:** `org.eclipse.jgit:org.eclipse.jgit`.
- **SSH transport:** JGit Apache MINA sshd transport.
- **Host verification:** pinned GitHub SSH host fingerprints only.
- **Scoping:** Hilt `@Singleton`.
- **Async:** `Dispatchers.IO`; JGit operations are blocking.

See [`../../docs/adr/0003-github-only-ssh-host-verification.md`](../../docs/adr/0003-github-only-ssh-host-verification.md) for why v1 supports GitHub SSH remotes only.

## Responsibility

Manage the pass store working copy and synchronize it with GitHub over SSH. Git Sync and the entry index resolve the working copy from the same saved path.

## Sync Model

- **First run:** `git clone <remote> <local-path>` via SSH.
- **Subsequent syncs:** `git pull --ff-only`.
- **Trigger:** manual sync and app flows that call the sync layer.
- **Login edit:** Commit only the selected encrypted entry. The unsigned commit uses the first GPG UID for its identity and the `pass edit` message.
- **Push:** After a successful commit, push the current branch from the application scope. A rejected or failed push leaves the commit local and displays a snackbar.
- **Serialization:** Clone, pull, commit, and push share one repository lock.

## SSH Auth

Git auth uses the selected **SSH Auth Source** from onboarding:

- generated **Device key**, or
- **GPG auth subkey** derived key.

After onboarding, sync is source-agnostic: it reads the stored SSH keypair from `SshKeyStore`.

## Local Storage

- Git repo is cloned into app-private internal storage.
- Encrypted `.gpg` files are stored as-is and are not decrypted to disk.
- Git objects serve as the offline cache; there is no separate entry file cache.

## Error States

- Remote unreachable → surface last-sync timestamp and continue with local copy where possible.
- SSH auth failure → surface an actionable error.
- Non-fast-forward/diverged history → fail with `NotFastForward`; no merge or rebase is attempted.
- Unsupported host → host verification fails unless the host key matches GitHub's pinned fingerprints.

## Interfaces

- `clone(remoteUrl: String, localPath: Path, sshKeyPair: SshKeyPair?, progressMonitor: ProgressMonitor?)` — initial clone.
- `pull(): SyncResult` — fast-forward pull, returning changed `.gpg` paths and last sync time.
- `commitEdit(path, prepare)` — validate and prepare an encrypted edit under the repository lock, then commit that file.
- `push()` — push the current branch and verify the remote accepted the update.
- `syncStatus(): SyncStatus` — last sync time, local commit, remote reachability.
- `lastCommitForFile(repoRelativePath: String): FileCommitInfo?` — metadata for entry detail UI.

## Non-Goals (v1)

- HTTPS authentication
- GitLab/Gitea/self-hosted SSH remotes
- Custom known_hosts / trust-on-first-use host enrollment
- Multiple remotes
- Branch selection UI (assumes the cloned default branch / current repo branch)
- Manual push retry and divergence recovery (issue #24)
