# Session / Crypto Refactor Plan

Split the monolithic `KeyManagementImpl` into two modules with clear ownership boundaries.

## Problem

`KeyManagementImpl` owns too much: session lifecycle, Keystore ops, biometric, GPG/SSH crypto, and two independent inactivity timers. Two bugs exist:

1. `startSession()` never starts the inactivity timer — idle sessions never time out
2. Cached passphrase path in `getGpgKey()` skips `scheduleInactivityTimeout()` — timer not reset on every use
3. Biometric gate is soft (app-level) — `setUserAuthenticationRequired(false)`, not hardware-enforced

## Design

### Module Split

| Module  | Interface           | Impl             | Owns                                                                              |
| ------- | ------------------- | ---------------- | --------------------------------------------------------------------------------- |
| Session | `SessionOperations` | `SessionManager` | Keystore key, encrypted passphrase blob, session inactivity timer, biometric gate |
| Crypto  | `CryptoOperations`  | `CryptoService`  | GPG/SSH key ops, in-memory passphrase cache, biometric cache timer                |

### Session Module (`SessionOperations` / `SessionManager`)

Responsible for the global session used by both app and autofill.

```kotlin
sealed class SessionState {
    object Active : SessionState()
    data class Inactive(val reason: EndReason) : SessionState()
}
enum class EndReason { TIMEOUT, MANUAL, REBOOT }
```

Default state: `Inactive(REBOOT)`.

**Key interfaces:**

- `createSession(passphrase)` — encrypt passphrase into Keystore, start inactivity timer
- `endSession()` — delete Keystore entry + blob, emit `Inactive`
- `touchSession()` — reset inactivity timer
- `isSessionActive(): Boolean` — Keystore entry check
- `getPassphrase(activity): String` — `BiometricPrompt(CryptoObject(cipher))` → hardware-authenticated cipher → decrypt blob
- `sessionState: Flow<SessionState>`

**Keystore key:** `setUserAuthenticationRequired(true)`, no `setUserAuthenticationValidityDurationSeconds` call (defaults to `-1`). This means the key has no time-based auth window — `Cipher.init()` only succeeds when the `Cipher` is wrapped in a `CryptoObject` passed through `BiometricPrompt`. The OS cryptographically links the cipher to the biometric event; there is no window where the key is freely usable after a previous auth. PIN/pattern accepted as fallback (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`). Devices with neither cannot use the app — no passphrase fallback path.

**Inactivity timer:** starts on `createSession()`, resets on every `touchSession()` call. Duration from `DataStore` (0 = manual lock only).

### Crypto Module (`CryptoOperations` / `CryptoService`)

Responsible for GPG/SSH ops and the in-memory passphrase cache (biometric UX layer).

**Biometric cache:** plaintext passphrase held in memory after hardware biometric unlock. Cleared after biometric cache timeout (hardcoded 5 min, configurable in 2.0) or on `sessionState` `Inactive` emission.

**Key interfaces:**

- `startSession(passphrase)` — validate passphrase against GPG key → `sessionOperations.createSession(passphrase)`
- `getGpgKey(activity)` — cache hit: use cached passphrase; cache miss: `sessionOperations.getPassphrase()` → cache result; always calls `sessionOperations.touchSession()`
- `clearAllKeys()` — `sessionOperations.endSession()` + wipe key blobs

### Consumer Mapping

| Consumer                         | Injects             |
| -------------------------------- | ------------------- |
| App UI (entry decrypt, settings) | `CryptoOperations`  |
| AutofillService                  | `SessionOperations` |
| Credential Manager Provider      | `SessionOperations` |
| Boot receiver                    | `SessionOperations` |

## What Gets Deleted

- `KeyManagementImpl.kt`
- `KeyManagement` interface
- `getGpgKeyWithPassphrase()` — no-biometric path removed entirely

## Out of Scope

- Biometric cache timeout configurable (2.0 backlog)
- SSH biometric unlock (2.0 backlog)
- Autofill passphrase fallback for no-biometric devices (2.0 backlog)
