# Key Management Module

## Implementation
- **Keystore ops:** `android.security.keystore.KeyStore` + `KeyGenerator` (AES-256-GCM wrapping key)
- **Biometric:** `androidx.biometric.BiometricPrompt` + `BiometricManager`
- **Scoping:** Hilt `@Singleton` — single instance shared across UI and `AutofillService`
- **Async:** Coroutines (`Dispatchers.IO` for key unwrap/wrap operations)

## Responsibility
Secure storage and lifecycle management for all cryptographic key material on the device: the GPG private key and the SSH private key.

## Key Storage Model
Both keys follow the same pattern:

```
[Key blob] ← encrypted with AES-256-GCM
     ↑
[Wrapping key] ← stored in Android Keystore (hardware-backed TEE)
                  bound to: device unlock + biometric confirmation
```

- Key blobs live in app-private internal storage (`/data/data/<pkg>/files/keys/`)
- Wrapping keys live in Android Keystore — never exposed to userspace
- GPG ops (decryption) happen in userspace after unwrapping the key
- SSH ops (git transport) happen in userspace after unwrapping the key

## Session Lock
- App session: inactivity timeout (default 5 min, configurable). After timeout, wrapping key requires re-auth to use.
- Autofill fills: always trigger a biometric prompt regardless of session state.

## Interfaces
- `importGpgKey(armoredKey: String, passphrase: String?)` — import, decrypt, re-encrypt under Keystore-backed wrapping key
- `generateSshKey(): PublicKey` — generate Ed25519 keypair, store encrypted, return public key for user to register on remote
- `getGpgKey(biometricPrompt): GpgPrivateKey` — unlock and return decrypted GPG key for a single operation
- `getSshKey(biometricPrompt): SshPrivateKey` — unlock and return decrypted SSH key for git transport
- `clearAllKeys()` — wipe both key blobs and Keystore entries (factory reset / sign-out)

## Acceptance Checklist

```
[manual] importGpgKey() accepts armored key with passphrase
[manual] importGpgKey() accepts armored key without passphrase
[manual] importGpgKey() rejects malformed armored key with clear error
[manual] imported GPG key survives app restart (blob persists, Keystore entry intact)
[manual] generateSshKey() returns valid Ed25519 public key in OpenSSH format
[manual] generated SSH key survives app restart
[manual] getGpgKey() requires biometric when session is locked
[manual] getGpgKey() skips biometric prompt within active session window
[manual] getSshKey() requires biometric when session is locked
[manual] clearAllKeys() removes both key blobs from app-private storage
[manual] clearAllKeys() removes Keystore entries (verify via KeyStore.aliases())
[manual] keys inaccessible after session timeout (app re-prompts biometric)
[manual] app handles biometric not enrolled — degrades to device PIN gracefully
```

## Android 16 Notes
- **Identity Check:** On Android 16+, the OS enforces biometric-only auth for accessing credentials outside trusted locations at the platform level. This reinforces our biometric gate without requiring app-side changes.
- **Restore Credentials (v2 candidate):** `CreateRestoreCredentialRequest` (API 36) enables encrypted cross-device key transfer via Google's backup infrastructure. A future version could use this to restore the GPG/SSH key blobs to a new device during setup, replacing the manual re-import flow.

## Non-Goals (v1)
- Hardware security key (YubiKey/OpenPGP card) support
- Multiple GPG keys / subkeys
- Key rotation
- Cross-device key restore (Restore Credentials API)
