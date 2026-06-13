# Domain Context

## Pass Store

Entries are GPG-encrypted files in the standard `pass` format:

- Line 1: the secret (password, card number, etc.)
- Lines 2+: key-value pairs (`key: value`)

## Entry Path Convention

Pass entries are `.gpg` files. `domain` and `username` are parsed from the file path at index time — no decryption required.

**Rule:** strip `.gpg`, split by `/`, take the last two segments.

- `username` = filename minus `.gpg` (always the last segment)
- `domain` = immediate parent directory name (second-to-last segment), or `null` if the file is at the store root

Any path segments above the parent directory are organizational and are discarded. Both of these resolve identically:

```
github.com/alice.gpg          → domain=github.com  username=alice
web/github.com/alice.gpg      → domain=github.com  username=alice
```

For **Card Entries**, the leading `cards/` or `credit-cards/` segment is stripped first, then the same rule applies:

```
cards/visa/my-card.gpg        → domain=visa        username=my-card
cards/my-card.gpg             → domain=null         username=my-card
```

**`domain` semantics differ by entry type:**

| Entry type               | `domain` used for                                                                                                         |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| Login entry              | Autofill matching (DNS subdomain + package name reversal) AND display label                                               |
| Card entry               | Display label only — cards match all card forms regardless of domain                                                      |
| Any entry, `domain=null` | Never auto-suggested for autofill (`resolve`/`resolveByPackage` skip them); reachable only via explicit search or browser |

The `domain` field is expected to be a DNS domain name for login entries. Non-DNS parent directories (e.g. `personal/`, `work/`) must not be the immediate parent of a login entry or they will be treated as the domain.

## Git Sync

The app syncs the pass store via JGit over SSH. Key constraints:

- **GitHub only.** Host verification accepts only GitHub's known SSH fingerprints (ed25519, ecdsa-nistp256, rsa). Any other host (GitLab, self-hosted, etc.) will fail host verification.
- **Fast-forward only.** Pull rejects diverged history (`--ff-only`). If the remote was force-pushed, sync fails with `NotFastForward` — no merge, no rebase.
- Pull returns a `SyncResult` with `newEntries` (added + modified `.gpg` paths) and `removedEntries` (deleted `.gpg` paths). A rename appears as a delete + add, not a single rename event.

### SSH Auth Source

The SSH key used for git remote access has one of two **sources**, chosen during onboarding (clone step, 2/2):

- **Device key** — an ed25519 keypair generated on device. Default when the imported GPG key carries no usable auth subkey.
- **GPG auth subkey (derived key)** — when the imported GPG key carries an ed25519 subkey flagged for **Authentication**, its private material is extracted (once, at clone time, after a GPG passphrase prompt) and stored as the SSH key. Only ed25519 auth subkeys qualify; RSA/ECDSA auth subkeys are ignored (treated as no usable key). The newest qualifying subkey is chosen automatically.

Regardless of source, the resulting keypair is stored identically in `SshKeyStore` (Keystore-wrapped, no passphrase at sync time), so all downstream git sync is source-agnostic. The chosen source is persisted (provenance) for display in Settings. The derived key is **not** auto-registered on GitHub by uploading the GPG key — the `ssh-ed25519` public key must be added under GitHub → SSH keys separately.

## Card Entry

A **Card Entry** is a pass entry stored under the `cards/` or `credit-cards/` top-level directory. It is detected at index time from the file path — no decryption required.

**File format:**

```
4532015112830366
cvv: 123
expiry: 12/27
cardholder: John Smith
```

- Line 1: card number
- `cvv`: security code
- `expiry`: expiry date as `MM/YY`
- `cardholder`: name as it appears on the card

Card entries are filled as a unit — selecting one fills all card fields in the target form simultaneously.
