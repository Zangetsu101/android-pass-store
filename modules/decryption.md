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
[Key Management Module] ← biometric prompt
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

## Non-Goals (v1)
- Symmetric encryption support (pass `-c`)
- OTP parsing
- Custom field extraction
