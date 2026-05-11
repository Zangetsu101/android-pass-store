# Key Management

Two modules with clear ownership boundaries — split from the original monolithic `KeyManagementImpl`.

---

## Session Module

### Interface: `SessionOperations` / Impl: `SessionManager`

**Responsibility:** Owns the encrypted passphrase, Keystore lifecycle, and session inactivity timer.

**Implementation:**

- `android.security.keystore.KeyStore` + `KeyGenerator`
- `androidx.biometric.BiometricManager` (status check only — no UI)
- Hilt `@Singleton`
- `DataStore<Preferences>` for session timeout preference
- Coroutines (`Dispatchers.IO`) for timer

### Session State Flow

```kotlin
sealed class SessionState {
    object Active : SessionState()
    data class Inactive(val reason: EndReason) : SessionState()
}

enum class EndReason { TIMEOUT, MANUAL, REBOOT }
```

Default: `Inactive(REBOOT)` — covers fresh install, first launch, actual device reboot.

### Key Storage

```
[Passphrase] ← encrypted under AES-256-GCM Keystore key
                Keystore key: setUserAuthenticationRequired(true), no validity duration (-1)
                Cipher.init() only succeeds via CryptoObject from BiometricPrompt — hardware enforced, no time window
                PIN/pattern accepted as fallback (BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                devices with no biometric and no PIN cannot use the app
```

### Interfaces

- `createSession(passphrase: String)` — encrypt passphrase into Keystore, start inactivity timer
- `endSession()` — delete Keystore entry + encrypted blob
- `touchSession()` — reset inactivity timer
- `isSessionActive(): Boolean` — check Keystore entry exists
- `getPassphrase(activity: FragmentActivity): String` — `BiometricPrompt(CryptoObject(cipher))` → hardware-authenticated cipher → Keystore decrypt
- `sessionState: Flow<SessionState>` — emits on session start/end

### Session Lifecycle

- **Create:** `CryptoService.startSession(passphrase)` validates → calls `createSession()` → stores encrypted passphrase → starts inactivity timer
- **Active:** `getPassphrase(activity)` → biometric → Keystore decrypt → return plaintext
- **Touch:** `touchSession()` called by `CryptoService` on every `getGpgKey()` call (cached or not) — resets inactivity timer
- **End triggers:**
  - Inactivity timeout — timer fires → `endSession()` → emits `Inactive(TIMEOUT)`
  - Manual lock — `endSession()` called → emits `Inactive(MANUAL)`
  - Device reboot — `ACTION_BOOT_COMPLETED` receiver → `endSession()` → initial state `Inactive(REBOOT)`

### Session Settings

- **Timeout mode:** inactivity timer resets on every key access, duration user-configurable via `DataStore`
- **Manual lock mode:** `timeout = 0` — session persists until user explicitly locks

---

## Crypto Module

### Interface: `CryptoOperations` / Impl: `CryptoService`

**Responsibility:** GPG/SSH key operations and in-memory passphrase cache (biometric cache layer).

**Implementation:**

- `org.pgpainless:pgpainless-core` (BouncyCastle wrapper)
- BouncyCastle for SSH keypair
- Hilt `@Singleton`
- Injects `SessionOperations`
- Observes `sessionState: Flow<SessionState>` to clear cache on session end

### Biometric Cache

```
[Passphrase] ← held in memory after biometric unlock
                cleared after biometric cache timeout (default: 5 min, future: configurable)
                cleared immediately on Inactive emission from sessionState
```

- `getGpgKey()` skips biometric if passphrase is cached — returns immediately
- Every `getGpgKey()` call (cached or not) calls `sessionOperations.touchSession()`
- Cache evicted on session end via `sessionState` observation

### Interfaces

- `startSession(passphrase: String)` — validate passphrase against GPG key → `sessionOperations.createSession(passphrase)`
- `getGpgKey(activity: FragmentActivity): GpgPrivateKey` — `isSessionActive()` check → `sessionOperations.getPassphrase()` (CryptoObject biometric) → cache plaintext passphrase → `touchSession()` → unlock GPG key
- `importGpgKey(armoredKey: String)` — validate key is passphrase-protected, store blob; reject unprotected keys
- `generateSshKey(): String` — generate Ed25519 keypair, store encrypted under device-unlock Keystore key, return OpenSSH public key
- `getSshKey(): SshPrivateKey` — unwrap SSH key (device-unlock bound, no biometric required)
- `clearAllKeys()` — `sessionOperations.endSession()` + wipe all key blobs + delete SSH Keystore entry

---

## Key Storage Model

### GPG Key

```
[Armored GPG key blob] ← stored as-is in app-private internal storage
                          (/data/data/<pkg>/files/keys/gpg.asc)
                          must be passphrase-protected — unprotected keys rejected at import
                          passphrase held in Keystore during active session
```

### SSH Key

```
[SSH key blob] ← encrypted with AES-256-GCM
     ↑
[Wrapping key] ← stored in Android Keystore (hardware-backed TEE)
                  bound to: device unlock only (no biometric required)
```

---

## Consumer Mapping

| Consumer                            | Module              |
| ----------------------------------- | ------------------- |
| App UI (entry decrypt)              | `CryptoOperations`  |
| App Settings (lock, timeout config) | `CryptoOperations`  |
| AutofillService                     | `SessionOperations` |
| Credential Manager Provider         | `SessionOperations` |
| Boot completed receiver             | `SessionOperations` |

---

## Autofill

- AutofillService injects `SessionOperations` directly — no `CryptoOperations` dependency
- Checks `getSessionStatus()` before proceeding
- **Active:** `sessionOperations.getPassphrase(activity)` → hardware biometric (CryptoObject) → decrypt entry
- **NoActiveSession:** prompt user to open app and start a session first

---

## Android 16 Notes

- **Identity Check:** On Android 16+, the OS enforces biometric-only auth for accessing credentials outside trusted locations at the platform level. Reinforces our biometric gate without app-side changes.

---

## Non-Goals (v1)

- Hardware security key (YubiKey/OpenPGP card) support
- Multiple GPG keys / subkeys
- Key rotation
- Cross-device key restore (Restore Credentials API)
- SSH biometric unlock
- Import of unprotected GPG keys (user sets passphrase during import)

---

## 2.0 Backlog

- **Biometric cache timeout configurable** — separate slider in Settings
- **SSH biometric unlock** — optional setting to require biometric for SSH key access
- **Import unprotected GPG key** — prompt user to set passphrase during import
- **Restore Credentials** — `CreateRestoreCredentialRequest` (API 36) for encrypted cross-device key transfer
