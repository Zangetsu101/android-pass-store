# Decryption Module

## Implementation

- **OpenPGP:** `org.pgpainless:pgpainless-core` (Bouncy Castle wrapper).
- **Async:** `Dispatchers.IO` for file I/O and OpenPGP processing.
- **Entry source:** encrypted `.gpg` files from the local git working copy.

## Responsibility

Decrypt a single pass entry with the imported GPG secret ring and parse it into credentials or card fields for display/fill.

## Decryption Flow

```
encryptedFile (.gpg)
       │
       ▼
[GPG key provider]
       │ obtains passphrase through either cached app UI path or direct external-fill biometric path
       ▼
[OpenPGP decrypt]
       │
       ▼
plaintext content
       │
       ▼
[Pass file parser]
```

## File Parsing

For login entries:

```
line 1  → password
line 2+ → notes for display
username → first `username`, `login` or `user` line, else path-derived PassEntry.username
```

`parseEntryNotes` splits the notes into **fields** and other lines. A field is a line that matches `key: value`: the key is one word with no space before the colon, and at least one space comes after the colon. The other lines keep their order. The username keys use the first field that it finds, in the order `username`, `login`, `user`, and the key ignores case.

Every `otpauth://` line is removed from password/notes; the first is parsed as TOTP or a safe error. OTP-only entries have an empty password and are not returned by external credential surfaces. The OTP module uses RFC 6238 HMAC via `javax.crypto.Mac` (SHA1/SHA256/SHA512, 6–8 digits, positive period). Secrets remain in memory alongside the decrypted credentials and are released on the existing detail lock.

For Card Entries:

```
line 1       → card number
cvv          → security code
expiry       → expiry date
cardholder   → cardholder name
```

Card-entry field names and path rules are defined in [`../../CONTEXT.md`](../../CONTEXT.md).

## Credentials Model

```kotlin
data class Credentials(
    val password: CharArray,
    val notes: String,
    val username: String,
    val otp: Otp? = null,
)
```

The same model is used for login passwords and card numbers: `password` holds the first-line secret, while `notes` holds remaining plaintext lines.

## Memory and Disk Handling

Decrypted secrets are never written to disk by the app. They are held only in process memory for display/fill and are not guaranteed to be scrubbed from all heap buffers; OpenPGP processing and notes parsing currently involve immutable `String` values.

## Interfaces

- `decrypt(entry: PassEntry, activity: FragmentActivity): Credentials` — decrypts one entry, obtaining the GPG passphrase through the injected key provider path.
- App UI decryption uses the cached passphrase provider while the session is active.
- External credential surfaces use the direct passphrase provider, requiring fresh biometric confirmation per fill.

## Non-Goals (v1)

- HOTP generation and OTP secret creation
- Arbitrary custom field extraction beyond current Card Entry fields
- Strong heap-scrubbing guarantees
