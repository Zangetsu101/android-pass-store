# PassDroid — Architecture

> Read-only Android client for the `pass` password store with autofill integration.

## Implementation Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + StateFlow |
| Dependency injection | Hilt |
| Navigation | Navigation Compose (type-safe routes, Navigation 2.8+) |
| Git | JGit + Apache MINA sshd |
| OpenPGP | PGPainless (Bouncy Castle wrapper) |
| Local persistence | Preferences DataStore |
| Concurrency | Kotlin Coroutines + Flow (`Dispatchers.IO` for blocking ops) |
| Min SDK | API 26 (Android 8) |
| Target SDK | API 36 (Android 16) |
| Testing | Unit tests for pure logic (PassStore matching, path parsing, fuzzy search) |

---

## Design Decisions (TL;DR)

| Concern | Decision |
|---|---|
| Sync transport | Git remote via SSH |
| Git auth | SSH keypair generated on-device |
| GPG key | Imported from existing keyring |
| Key storage | Encrypted blob (app-private) + Keystore-backed wrapping key |
| Autofill surface | `AutofillService` (Android 8+) + Credential Manager (Android 14+) |
| Passkey support | Out of scope |
| Entry matching | Directory/filename convention + fuzzy match |
| Username source | Last path component (e.g. `web/github.com/alice` → `alice`) |
| Password source | First line of decrypted file |
| OTP | Out of scope (v1) |
| Write support | Read-only (v1) |
| Offline | Full offline after first sync (local git clone, decrypt on demand) |
| Session lock | Timed (5 min inactivity); autofill always prompts biometric |

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
│          │   Keystore, biometric) │                     │
│          └────────────────────────┘                     │
└─────────────────────────────────────────────────────────┘
                          │ SSH
                          ▼
               ┌──────────────────┐
               │  Git Remote      │
               │ (GitHub / Gitea) │
               └──────────────────┘
```

---

## Modules

| # | Module | Responsibility |
|---|---|---|
| 1 | [Key Management](modules/key-management.md) | GPG + SSH key import/generation, Keystore-backed storage, biometric unlock |
| 2 | [Git Sync](modules/git-sync.md) | Clone and pull the pass repo from a remote over SSH |
| 3 | [Pass Store](modules/pass-store.md) | Index `.gpg` paths, resolve autofill queries, fuzzy search |
| 4 | [Decryption](modules/decryption.md) | GPG-decrypt entries, parse password from line 1 |
| 5 | [Autofill](modules/autofill.md) | `AutofillService` (Android 8+) and Credential Manager (Android 14+) integration |
| 6 | [UI](modules/ui.md) | Onboarding, entry browser, sync panel, settings |

---

## Data Flow: Autofill Fill

```
Android System
    │  fill request (domain/package)
    ▼
AutofillService
    │  PassStore.resolve(domain) → [entry candidates]  ← no decryption
    │  return FillResponse with locked datasets
    ▼
User selects candidate
    │  biometric prompt
    ▼
KeyManagement.getGpgKey()
    │
    ▼
Decryption.decryptForAutofill(entry)
    │  password = line 1
    │  username = path component
    ▼
Android fills fields
```

## Data Flow: First-Run Setup

```
User
 │ enters git remote URL
 ▼
GitSync.clone() ← SSH key from KeyManagement
 │
 ▼
PassStore.buildIndex()
 │
 ▼
App ready
```

---

## Android 16 (API 36) Relevant Changes

| Feature | Impact |
|---|---|
| **Identity Check** | OS enforces biometric-only auth (no PIN fallback) for credential access outside trusted locations — our per-fill biometric design is compliant automatically |
| **`BiometricPromptData`** | Embeds biometric prompt directly into Credential Manager flow on Android 16+, removing the separate `IntentSender` round-trip (autofill module) |
| **Restore Credentials** | `CreateRestoreCredentialRequest` enables encrypted cross-device GPG/SSH key transfer — v2 candidate to replace manual key re-import on new devices (key management module) |

---

## Security Properties

| Property | Mechanism |
|---|---|
| GPG key at rest | AES-256-GCM blob, wrapping key in hardware TEE |
| SSH key at rest | Same as GPG key |
| Decrypted passwords | Never written to disk; zeroed from memory after use |
| Autofill gate | Biometric required per fill, always |
| App session | Inactivity timeout (default 5 min) |
| Clipboard | Auto-cleared after 45 sec |
| Local git repo | App-private storage (inaccessible to other apps) |

---

## Out of Scope (v1)

- Write support (create/edit/delete entries)
- OTP / pass-otp
- Passkeys / FIDO2
- HTTPS git authentication
- Multiple GPG keys or recipients
- Hardware security key (YubiKey)
- pass extensions / custom fields
- Browser extension bridge
