# Git Sync Module

## Responsibility
Clone and pull the pass git repository from a remote over SSH. Manages the local git working copy that all other modules read from.

## Sync Model
- **First run:** `git clone <remote> <local-path>` via SSH
- **Subsequent syncs:** `git pull --ff-only` (fast-forward only — no merge conflicts possible in read-only mode)
- **Trigger:** on app launch + manual pull-to-refresh in UI
- **No push:** read-only, no writes to remote

## Local Storage
- Git repo cloned into app-private internal storage
- Encrypted `.gpg` files stored as-is (not decrypted to disk)
- Git objects serve as the offline cache — no separate file cache needed

## Error States
- Remote unreachable → surface last-sync timestamp, continue with local copy
- SSH auth failure → surface error with link to SSH key export (for re-registration on remote)
- Non-fast-forward → surface warning (someone force-pushed); offer `git reset --hard origin/main`

## Interfaces
- `clone(remoteUrl: String, localPath: Path)` — initial clone
- `pull(): SyncResult` — fast-forward pull, returns `(newEntries, removedEntries, lastSyncTime)`
- `syncStatus(): SyncStatus` — last sync time, local commit, remote reachable

## Non-Goals (v1)
- HTTPS authentication
- Multiple remotes
- Branch selection (assumes `main`)
- Push / write-back
