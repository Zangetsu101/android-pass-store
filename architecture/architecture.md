# PassAndroid — Architecture

> Current-state technical architecture for a read-only Android client for the `pass` password store.

Domain vocabulary and store conventions live in [`../CONTEXT.md`](../CONTEXT.md). This document describes the implementation shape, module boundaries, and current security properties.

## Implementation Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + StateFlow |
| Dependency injection | Hilt |
| Navigation | AndroidX Navigation 3 |
| Git | JGit + Apache MINA sshd |
| OpenPGP | PGPainless (Bouncy Castle wrapper) |
| Local persistence | Preferences DataStore + app-private files |
| Concurrency | Kotlin Coroutines + Flow (`Dispatchers.IO` for blocking ops) |
| Android SDK | minSdk 26, compileSdk 37, targetSdk 36 |
| Testing | Unit tests for core logic and ViewModels; Maestro e2e flows for onboarding and entry browsing |

---

## Design Decisions (TL;DR)

| Concern | Current decision |
|---|---|
| Sync transport | GitHub SSH remotes via JGit |
| Git auth | SSH key selected during onboarding from an **SSH Auth Source**: generated **Device key** or derived **GPG auth subkey** |
| GPG key | Imported armored secret key ring; must include a **Usable Encryption Key** |
| Long-lived key storage | GPG/SSH blobs encrypted with AES-GCM using Android Keystore wrapping keys, without per-read biometric auth |
| Session passphrase storage | Encrypted session blob protected by a biometric-gated Android Keystore RSA-OAEP key; StrongBox is preferred only for manual-clear sessions |
| Autofill surface | `AutofillService` (Android 8+), inline suggestions (Android 11+), and Credential Manager provider (Android 14+) |
| External fill gate | Every **External Credential Surface** requires fresh biometric confirmation before releasing a secret |
| Entry matching | Conservative automatic matching; fuzzy matching only in explicit user search |
| Username source | Last path component (e.g. `web/github.com/alice` → `alice`) |
| Secret source | First line of decrypted pass file; remaining lines are notes/fields |
| Card autofill | Supported for **Card Entries** |
| Passkey support | Out of scope |
| OTP | Out of scope (v1), future work |
| Write support | Read-only (v1) |
| Offline | Full offline after first sync (local git clone, decrypt on demand) |
| Session lock | Timed inactivity lock; app UI may use passphrase cache while session is active |

See [`docs/adr/0002-threat-model.md`](../docs/adr/0002-threat-model.md) for the at-rest key protection trade-off, [`docs/adr/0003-github-only-ssh-host-verification.md`](../docs/adr/0003-github-only-ssh-host-verification.md) for GitHub-only SSH host verification, [`docs/adr/0004-keep-rsa-oaep-for-session-passphrase.md`](../docs/adr/0004-keep-rsa-oaep-for-session-passphrase.md) for the session passphrase storage choice, and [`docs/adr/0005-prefer-strongbox-only-for-manual-clear-session-cache.md`](../docs/adr/0005-prefer-strongbox-only-for-manual-clear-session-cache.md) for the StrongBox policy.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Android App                        │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │   UI Layer  │  │ AutofillSvc  │  │CredentialMgr  │  │
│  │  (Browser,  │  │ (Android 8+) │  │ (Android 14+) │  │
│  │  Onboarding,│  └──────┬───────┘  └──────┬────────┘  │
│  │  Settings)  │         │                 │            │
│  └──────┬──────┘         └────────┬────────┘            │
│         │                         │                     │
│         ▼                         ▼                     │
│  ┌─────────────────────────────────────────────────┐    │
│  │               Pass Store Module                 │    │
│  │   (index, search, path parsing, entry lookup)   │    │
│  └────────────────────┬────────────────────────────┘    │
│                       │                                 │
│          ┌────────────┴────────────┐                    │
│          ▼                         ▼                    │
│  ┌───────────────┐      ┌──────────────────────┐        │
│  │  Git Sync     │      │  Decryption Module   │        │
│  │  Module       │      │  (GPG, file parser)  │        │
│  └───────┬───────┘      └──────────┬───────────┘        │
│          │                         │                    │
│          └────────────┬────────────┘                    │
│                       ▼                                 │
│          ┌────────────────────────┐                     │
│          │   Key Management       │                     │
│          │   Module               │                     │
│          │  (SSH key, GPG key,    │                     │
│          │   session, biometric)  │                     │
│          └────────────────────────┘                     │
└─────────────────────────────────────────────────────────┘
                          │ SSH
                          ▼
               ┌──────────────────┐
               │  GitHub Remote   │
               └──────────────────┘
```

---

## Modules

| # | Module | Responsibility |
|---|---|---|
| 1 | [Key Management](modules/key-management.md) | GPG + SSH key import/generation, Keystore-backed storage, session passphrase, biometric unlock |
| 2 | [Git Sync](modules/git-sync.md) | Clone and pull the pass repo from GitHub over SSH |
| 3 | [Pass Store](modules/pass-store.md) | Index `.gpg` paths, resolve autofill queries, fuzzy user search |
| 4 | [Decryption](modules/decryption.md) | GPG-decrypt entries, parse password/card fields from pass files |
| 5 | [Autofill](modules/autofill.md) | `AutofillService` and Credential Manager provider integration |
| 6 | [UI](modules/ui.md) | Onboarding, entry browser, sync panel, settings |

---

## Data Flow: External Fill

```
Android System
    │  fill / credential request (domain or package)
    ▼
AutofillService or CredentialProviderService
    │  PassStore resolves candidates from indexed paths only ← no decryption
    │  return locked candidates backed by PendingIntent auth
    ▼
User selects candidate
    │  direct session passphrase path → fresh biometric prompt
    ▼
Decryption.decrypt(entry)
    │  password/card number = line 1
    │  username/card fields = path + notes
    ▼
Android fills target fields or returns PasswordCredential
```

## Data Flow: App Entry View

```
User taps entry in app browser
    │
    ▼
Decryption.decrypt(entry)
    │  cached passphrase may be used while app session is active
    ▼
Show password/card details and notes in app UI
```

## Data Flow: First-Run Setup

```
User imports GPG key
 │
 ▼
User selects SSH Auth Source / registers public key on GitHub
 │
 ▼
GitSync.clone() over SSH
 │
 ▼
PassStore.buildIndex()
 │
 ▼
App ready
```

---

## Android Version Notes

| Feature | Current impact |
|---|---|
| `AutofillService` | Supported on Android 8+ |
| Inline autofill suggestions | Supported on Android 11+ when the system provides inline presentation specs |
| Credential Manager provider | Supported on Android 14+ through `PasswordCredentialEntry` + `PendingIntent` auth |
| Android 16 Identity Check | Platform credential access may enforce biometric-only auth outside trusted locations; the app already requires biometric on external fill |

`BiometricPromptData` integration for Credential Manager inline biometric is future work, not current behavior.

---

## Security Properties

| Property | Current mechanism |
|---|---|
| GPG secret ring at rest | AES-256-GCM blob in app-private storage, wrapped by Android Keystore AES key; OpenPGP S2K passphrase still required to decrypt entries |
| SSH keypair at rest | AES-256-GCM blob in app-private storage, wrapped by Android Keystore AES key; no biometric required for sync |
| Session passphrase | Encrypted blob protected by biometric-gated Android Keystore RSA-OAEP key; decrypt requires `BiometricPrompt`; manual-clear sessions prefer StrongBox when available |
| External fill gate | Fresh biometric required per fill for AutofillService and Credential Manager |
| App session | Inactivity timeout; manual lock; does not survive session clearing/reboot |
| Decrypted secrets | Never written to disk; held only in process memory for display/fill and not guaranteed to be scrubbed from all heap buffers |
| Clipboard | Sensitive clip flag on supported Android versions; scheduled auto-clear using configured timeout |
| Local git repo | App-private storage; encrypted `.gpg` files remain encrypted on disk |
| Backups | Disabled with `allowBackup=false` |

---

## Current Limitations / Future Work

- OTP / `pass-otp` support and TOTP autofill
- Write support (create/edit/delete entries)
- Passkeys / FIDO2
- HTTPS git authentication
- GitLab/Gitea/self-hosted SSH remotes with explicit host-key enrollment
- Multiple GPG keys or recipients
- Hardware security key (YubiKey/OpenPGP card)
- pass extensions / custom fields beyond the current card-entry fields
- Credential Manager `BiometricPromptData` inline biometric integration
- Browser extension bridge
