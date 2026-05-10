# Decryption Module

## Implementation

- **OpenPGP:** `org.pgpainless:pgpainless-core` (Bouncy Castle wrapper)
- **Scoping:** Hilt `@Singleton`
- **Async:** `Dispatchers.IO` — decryption is CPU+I/O bound; never called on main thread
- **Memory safety:** `Credentials` fields are `CharArray` not `String` where possible, zeroed after use

## Responsibility

Decrypt a single `.gpg` file using the imported GPG private key and extract structured credentials.

## Decryption Flow

```
encryptedFile (.gpg)
       │
       ▼
[CryptoService] ← biometric prompt (or cached passphrase)
       │ returns GpgPrivateKey (in-memory, short-lived)
       ▼
[OpenPGP decrypt] (userspace, BouncyCastle or similar)
       │
       ▼
plaintext string
       │
       ▼
[File Parser] → Credentials(password, notes)
```

## File Parsing

```
line 1  → password
line 2+ → ignored (username comes from path, OTP out of scope)
```

## Credentials Model

```kotlin
data class Credentials(
    val password: String,
    val notes: String,      // remaining lines joined, for display
)
```

Decrypted credentials are **never written to disk**. Held in memory only for the duration of the autofill or display operation.

## Interfaces

- `decrypt(entry: PassEntry): Credentials` — triggers biometric if session locked, returns credentials
- `decryptForAutofill(entry: PassEntry): AutofillCredentials` — same, optimized path for autofill service (returns only password + username from path)

## Acceptance Checklist

```
[auto]   decrypt() returns password from line 1 of single-line file
           (fixture: test keypair + pre-encrypted .gpg)
[auto]   decrypt() returns password + notes from multi-line file
[auto]   decrypt() throws DecryptionError for file encrypted to wrong key
[auto]   decrypt() throws DecryptionError for corrupted/non-GPG file
[auto]   decryptForAutofill() returns only password (no notes allocation)
[auto]   Credentials.password is CharArray, zeroed after use
           (assert array is all-zero after explicit clear call)
[manual] decrypt() triggers biometric prompt on device when session locked
[manual] decrypt() succeeds without biometric prompt within active session
[manual] decrypted content never appears in app-private files/
           (verify via adb shell after a decrypt operation)
```

## Non-Goals (v1)

- Symmetric encryption support (pass `-c`)
- OTP parsing
- Custom field extraction
