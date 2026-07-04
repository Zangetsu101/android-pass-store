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
username → path-derived PassEntry.username
```

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

- OTP parsing
- Arbitrary custom field extraction beyond current Card Entry fields
- Strong heap-scrubbing guarantees
