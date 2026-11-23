# Domain Context

## Pass Store

Entries are GPG-encrypted files in the standard `pass` format:

- Line 1: the secret (password, card number, etc.), except for OTP-only entries
- Lines 2+: key-value pairs (`key: value`)

**Recipient**: An identifier in the nearest `.gpg-id` for the entry. An
identifier can be a long key ID, a fingerprint, or an email address. A
folder inherits its parent's recipients when it has no `.gpg-id`.

**Round-trip rule**: An edit preserves each hidden line, including every
**OTP Line**, in its position among the surrounding lines. It also preserves
the original line endings and final newline.

## OTP Entries

**Password + OTP Entry**: A pass entry with a password on line 1 and an `otpauth://` line below it.

**OTP-only Entry**: A pass entry with an **OTP Line** and no first-line password. It may also contain notes.

**OTP Line**: A line starting with `otpauth://` after leading spaces and tabs are removed. The first OTP Line wins; every OTP Line is excluded from passwords and notes, including invalid and unsupported lines. TOTP codes are read-only; HOTP and OTP on External Credential Surfaces are deferred.

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

For **Card Entries**, the first directory segment named exactly the lowercase `cards` or `credit-cards`, and every segment before it, are stripped first. The same rule then applies:

```
cards/visa/my-card.gpg                → domain=visa        username=my-card
cards/my-card.gpg                     → domain=null         username=my-card
finance/cards/visa/my-card.gpg        → domain=visa        username=my-card
finance/cards/my-card.gpg             → domain=null         username=my-card
```

**`domain` semantics differ by entry type:**

| Entry type               | `domain` used for                                                                                                         |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| Login entry              | Autofill matching (DNS subdomain + package name reversal) AND display label                                               |
| Card entry               | Display label only — cards match all card forms regardless of domain                                                      |
| Login entry, `domain=null` | Skipped by domain and package matching. Reachable through search, the entry browser, or Autofill History |

The `domain` field is expected to be a DNS domain name for login entries. Non-DNS parent directories (e.g. `personal/`, `work/`) must not be the immediate parent of a login entry or they will be treated as the domain.

## Git Sync

The app syncs the pass store via JGit over SSH. Key constraints:

- **GitHub only.** Host verification accepts only GitHub's known SSH fingerprints (ed25519, ecdsa-nistp256, rsa). Any other host (GitLab, self-hosted, etc.) will fail host verification.
- **Manual sync.** Fetch the tracked branch, rebase local commits onto it, then push any unpushed commits. With no local commits, the working copy fast-forwards. When you save an edit, the app tries to push it in the background.
- Sync returns a `SyncResult` with `newEntries` (added + modified `.gpg` paths) and `removedEntries` (deleted `.gpg` paths). The result lists a rename as a deletion and an addition.

**Unpushed Commit** — a commit reachable from the device branch and absent from the fetched remote branch. It remains local until a push succeeds.

**Conflict** — a sync cannot replay local changes safely, such as when both branches change the same encrypted entry. The local commits stay available. The user can explicitly discard them and start again from the fetched remote branch.

### SSH Auth Source

The SSH key used for git remote access has one of two **sources**, chosen during onboarding (clone step, 2/2):

- **Device key** — an ed25519 keypair generated on device. Default when the imported GPG key carries no usable auth subkey.
- **GPG auth subkey (derived key)** — when the imported GPG key carries an ed25519 subkey flagged for **Authentication**, its private material is extracted (once, at clone time, after a GPG passphrase prompt) and stored as the SSH key. Only ed25519 auth subkeys qualify; RSA/ECDSA auth subkeys are ignored (treated as no usable key). The newest qualifying subkey is chosen automatically.

Regardless of source, the resulting keypair is stored identically in `SshKeyStore` (Keystore-wrapped, no passphrase at sync time), so all downstream git sync is source-agnostic. The chosen source is persisted (provenance) for display in Settings. The derived key is **not** auto-registered on GitHub by uploading the GPG key — the `ssh-ed25519` public key must be added under GitHub → SSH keys separately.

## GPG Key Import

The keypair imported during onboarding (step 1/3) and stored as the full armored secret ring. Recommended export is `gpg --armor --export-secret-subkeys` — which keeps the master key offline as a **stub**.

**Usable Encryption Key**:
A subkey that is encrypt-capable, valid (not expired, not revoked), **and** carries private key material. Import requires at least one — without it the store can never be decrypted.
_Avoid_: "encryption key" alone (ambiguous — a stub advertises encrypt capability via its public flags but cannot decrypt).

**Stub Key** (public-only):
A key present in the secret ring with no private material — a gnu-dummy master (from `--export-secret-subkeys`) or a smartcard-diverted subkey. Reports capability and a passphrase state, but cannot extract a private key. Stubs are excluded from both the usable-encryption check and the passphrase check.

**Import is rejected when** (checked in this order):

1. **Malformed** — not parseable as a secret key ring (e.g. a public-only `gpg --export`).
2. **NoEncryptionKey** — no encrypt-capable subkey at all.
3. **ExpiredEncryptionKey** — an encrypt-capable subkey exists but all such keys are expired/revoked.
4. **PublicKeyOnly** — a valid encrypt-capable subkey exists but has no private material (stub/smartcard).
5. **NoPassphrase** — any private-bearing key is unprotected (S2K). Required by [ADR 0002](docs/adr/0002-threat-model.md): the at-rest ring's security reduces to S2K passphrase strength, for every secret it contains.

**Recipient mismatch** cannot be detected at import (no ciphertext or passphrase available then). A ring that passes all checks can still fail every decrypt if the store was encrypted to a different key — surfaced only at decrypt time as a "couldn't decrypt with the imported keyring" message.

## Credential Access

**External Credential Surface**:
A system-mediated flow that can release a secret to another app, currently AutofillService and Credential Manager.
_Avoid_: Autofill only, credential UI

Relationships:

- An **External Credential Surface** always requires fresh biometric confirmation before releasing a secret.
- The internal app browser may use the active session/passphrase cache; external credential surfaces may not.

**Fill Target**:
The place that asks for an autofill. It is the web domain of a browser form, or the package name of a native app.

**Autofill History**:
The record on the device of the entry that the user filled last on each **Fill Target**. It holds entry paths, never secrets.

Relationships:

- For a login form, the last login filled on that Fill Target is suggested first if it still exists in the index, even if domain or package matching misses it.
- For a card form, **Autofill History** only sorts the suggestions.
- Every fill still requires biometric confirmation.

## Card Entry

A **Card Entry** is a pass entry whose path contains a directory segment named exactly the lowercase `cards` or `credit-cards`; that segment marks the root of the card subtree. It is detected at index time from the file path — no decryption required.

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
