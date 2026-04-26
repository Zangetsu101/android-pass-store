# Key Management Module

## Implementation

- **Keystore ops:** `android.security.keystore.KeyStore` + `KeyGenerator`
- **Biometric:** `androidx.biometric.BiometricPrompt` + `BiometricManager`
- **Scoping:** Hilt `@Singleton` — single instance shared across UI and `AutofillService`
- **Async:** Coroutines (`Dispatchers.IO` for key operations)

## Responsibility

Secure storage and lifecycle management for all cryptographic key material on the device: the GPG private key and the SSH private key.

## Key Storage Model

### GPG Key

```
[Armored GPG key blob] ← stored as-is in app-private internal storage
                          passphrase held in Keystore during active session
```

- Armored key lives in app-private internal storage (`/data/data/<pkg>/files/keys/gpg.asc`)
- Must be passphrase-protected — unprotected keys are rejected at import
- Passphrase is never stored persistently on disk

### SSH Key

```
[SSH key blob] ← encrypted with AES-256-GCM
     ↑
[Wrapping key] ← stored in Android Keystore (hardware-backed TEE)
                  bound to: device unlock only (no biometric required)
```

- SSH key lives in app-private internal storage (`/data/data/<pkg>/files/keys/ssh`)
- Wrapping key is device-unlock bound — accessible for background git sync without user interaction

## Session Model

### Keystore Layout During Active Session

```
[Passphrase] ← encrypted under persistent biometric-bound Keystore key
                created at session start, deleted at session end
```

### Session Lifecycle

- **Start:** user enters passphrase → validated against armored GPG key → passphrase stored in Keystore → session active
- **Active:** biometric unlocks passphrase from Keystore → passphrase decrypts armored key → key used for operation
- **Biometric fallback:** device PIN/pattern via `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`
- **End triggers:**
  - Manual lock (user action) — Keystore entry deleted immediately
  - Inactivity timeout (user-configurable, timer resets on each decryption) — Keystore entry deleted on expiry
  - Device reboot — `ACTION_BOOT_COMPLETED` receiver deletes Keystore entry; passphrase required on next session

### Session Settings

- **Timeout mode:** inactivity timer (resets on each use), duration user-configurable
- **Manual lock mode:** session persists until user explicitly locks

## Autofill

- AutofillService checks session state (Keystore entry exists?) before proceeding
- **Session active:** biometric prompt → unlock passphrase → decrypt entry
- **Session inactive:** prompt user to open the app and start a session first

## Interfaces

- `importGpgKey(armoredKey: String)` — validate key is passphrase-protected, store blob; reject unprotected keys
- `startSession(passphrase: String)` — validate passphrase against stored armored key, store passphrase in Keystore
- `endSession()` — delete passphrase Keystore entry
- `generateSshKey(): PublicKey` — generate Ed25519 keypair, store encrypted under device-unlock Keystore key, return public key
- `getGpgKey(biometricPrompt): GpgPrivateKey` — biometric → unlock passphrase → decrypt armored key → return key for single operation
- `getSshKey(): SshPrivateKey` — unwrap SSH key from Keystore (device-unlock bound, no biometric required)
- `clearAllKeys()` — wipe both key blobs, delete all Keystore entries, end session

## Acceptance Checklist

```
[manual] importGpgKey() accepts passphrase-protected armored key
[manual] importGpgKey() rejects unprotected armored key with clear error
[manual] importGpgKey() rejects malformed armored key with clear error
[manual] imported GPG key survives app restart (blob persists)
[manual] startSession() succeeds with correct passphrase
[manual] startSession() fails with incorrect passphrase
[manual] generateSshKey() returns valid Ed25519 public key in OpenSSH format
[manual] generated SSH key survives app restart
[manual] getSshKey() accessible without biometric after device unlock
[manual] getGpgKey() requires biometric when session is active
[manual] getGpgKey() fallback to device PIN when biometric unavailable
[manual] session ends after inactivity timeout (app re-prompts passphrase)
[manual] manual lock immediately ends session
[manual] device reboot ends session (passphrase required on next open)
[manual] autofill succeeds when session is active (biometric prompt)
[manual] autofill prompts user to open app when session is inactive
[manual] clearAllKeys() removes both key blobs from app-private storage
[manual] clearAllKeys() removes all Keystore entries
```

## Android 16 Notes

- **Identity Check:** On Android 16+, the OS enforces biometric-only auth for accessing credentials outside trusted locations at the platform level. This reinforces our biometric gate without requiring app-side changes.

## Non-Goals (v1)

- Hardware security key (YubiKey/OpenPGP card) support
- Multiple GPG keys / subkeys
- Key rotation
- Cross-device key restore (Restore Credentials API)
- SSH biometric unlock
- Import of unprotected GPG keys (user sets passphrase during import)

## 2.0 Backlog

- **SSH biometric unlock** — optional setting to require biometric for SSH key access
- **Import unprotected GPG key** — allow import of passphrase-less keys, prompt user to set a passphrase during import
- **Restore Credentials** — `CreateRestoreCredentialRequest` (API 36) for encrypted cross-device key transfer
