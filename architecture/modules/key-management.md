# Key Management Module

Key management owns imported GPG material, SSH key material, the active session passphrase, and biometric-gated access to that passphrase.

See [`../../docs/adr/0002-threat-model.md`](../../docs/adr/0002-threat-model.md) for the at-rest key protection trade-off, and [`../../docs/adr/0004-keep-rsa-oaep-for-session-passphrase.md`](../../docs/adr/0004-keep-rsa-oaep-for-session-passphrase.md) for why session passphrase storage uses RSA-OAEP rather than biometric AES-GCM.

---

## Responsibilities

- Import and validate the user's armored GPG secret key ring.
- Detect and optionally derive an SSH key from a usable ed25519 GPG authentication subkey.
- Generate and store an on-device ed25519 SSH key when no auth subkey is used.
- Store long-lived GPG/SSH key blobs in app-private storage with Android Keystore wrapping keys.
- Store the active session passphrase behind a biometric-gated Android Keystore key.
- Expose cached app-UI passphrase access separately from direct external-fill passphrase access.
- Clear session/key material when the user locks or deletes local data.

---

## Session Module

### Interface: `SessionOperations` / Impl: `SessionManager`

**Responsibility:** Owns the encrypted active-session passphrase and session inactivity timer.

**Implementation:**

- `AndroidBiometricCryptoStore` for biometric-gated session passphrase storage.
- `androidx.biometric.BiometricPrompt` through `BiometricPrompter`.
- `DataStore<Preferences>` for session timeout and last-touched state.
- Hilt `@Singleton`.
- Coroutines for timer handling.

### Session State

```kotlin
sealed class SessionState {
    data object Active : SessionState()
    data class Inactive(val reason: EndReason) : SessionState()
}

enum class EndReason { TIMEOUT, MANUAL, BIOMETRIC_CHANGED, TIMEOUT_CHANGED }
```

Active sessions are cleared by timeout/manual lock/security changes and do not survive session clearing/reboot semantics.

### Session Passphrase Storage

```
[Passphrase] ← encrypted into app-private session blob
                protected by Android Keystore RSA-OAEP private key
                Keystore key: setUserAuthenticationRequired(true)
                API 30+: AUTH_BIOMETRIC_STRONG with validity duration 0
                decrypt Cipher must be authenticated through BiometricPrompt(CryptoObject)
```

RSA-OAEP is intentional here: session creation can encrypt with the public key without a biometric prompt, while passphrase retrieval requires biometric-gated private-key decrypt. This limits the stored passphrase size to the OAEP plaintext limit (~190 bytes for a 2048-bit key). `SessionManager.createSession()` rejects longer passphrases.

### Interfaces

- `createSession(passphrase: String)` — encrypt passphrase into session blob and start inactivity timer.
- `endSession(reason: EndReason = MANUAL)` — delete session key/blob and stop timer.
- `touchSession()` — reset inactivity timer.
- `getPassphrase(activity: FragmentActivity): String` — prompt biometric, decrypt session blob, and return passphrase.
- `sessionState: StateFlow<SessionState>` — emits active/inactive session state.

---

## Passphrase Providers

The app has two passphrase access paths:

| Path | Used by | Behavior |
|---|---|---|
| Direct passphrase provider | External credential surfaces | Always goes through `SessionOperations.getPassphrase(...)`, requiring fresh biometric before each fill |
| Cached passphrase provider | Internal app UI | Caches plaintext passphrase in memory for a short window during an active session |

The cache is cleared when the session becomes inactive.

---

## GPG Key Storage and Import

### Interface: `GpgKeyStore` / Impl: `GpgKeyStoreImpl`

**Storage:**

```
[Armored GPG secret ring] ← AES-256-GCM blob in app-private storage
                             wrapping key in Android Keystore
                             no biometric required to read blob
                             GPG passphrase still required to unlock private key material
```

**Import validation:** See [`../../CONTEXT.md`](../../CONTEXT.md) for **Usable Encryption Key**, **Stub Key**, and rejection semantics.

**Auth subkey support:** If the imported key ring contains a usable ed25519 authentication subkey, onboarding can derive the SSH key from that subkey. See **SSH Auth Source** in [`../../CONTEXT.md`](../../CONTEXT.md).

---

## SSH Key Storage

### Interface: `SshKeyStore` / Impl: `SshKeyStoreImpl`

**Storage:**

```
[SSH private key bytes] ← AES-256-GCM blob in app-private storage
[SSH public key bytes]  ← AES-256-GCM blob in app-private storage
                           wrapping keys in Android Keystore
                           no biometric required for sync
```

**Sources:**

- Generate an ed25519 **Device key** on device.
- Import an ed25519 seed extracted from a qualifying **GPG auth subkey**.

After storage, downstream git sync does not care which source produced the key.

---

## Consumer Mapping

| Consumer | Passphrase/key path |
|---|---|
| App UI entry decrypt | Cached passphrase provider + `GpgKeyStore` |
| AutofillService auth activity | Direct passphrase provider + `GpgKeyStore` |
| Credential Manager auth activity | Direct passphrase provider + `GpgKeyStore` |
| Git sync | `SshKeyStore` only; no biometric prompt |
| Settings clear session | `SessionOperations.endSession()` |
| Settings clear local data | `KeyManagement.clearAllKeys()` |

---

## Non-Goals (v1)

- Hardware security key (YubiKey/OpenPGP card) support
- Multiple GPG keys / recipients
- Key rotation
- Cross-device key restore
- SSH biometric unlock
- Import of unprotected GPG keys
- Passphrase-only unlock for devices with no secure biometric/session capability

---

## Future Work

- Configurable biometric cache timeout for internal app UI.
- Optional biometric gate for SSH key access.
- Restore Credentials API for encrypted cross-device key transfer.
