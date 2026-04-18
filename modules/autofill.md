# Autofill Module

## Responsibility
Expose pass credentials to Android's autofill surfaces: `AutofillService` (Android 8+) and Credential Manager (Android 14+).

## Components

**AutofillService** (`extends AutofillService`)
- Receives fill requests with app package name or web domain from Android
- Calls `PassStore.resolve()` to find candidates (no decryption)
- Returns a `FillResponse` with a dataset per candidate entry
- Each dataset is locked behind an `IntentSender` (biometric auth) — decryption only happens when user selects and confirms
- Populates `username` (from path) and `password` (from decrypted file line 1) into the target fields

**Credential Manager Provider** (Android 14+, `CredentialProviderService`)
- Wraps the same resolution + decryption logic
- Surfaces candidates in the Credential Manager bottom sheet
- Delegates to the same `DecryptionModule` after user selection

## Session / Biometric Gate
- Autofill always prompts biometric before decrypting, regardless of app session state
- Biometric result is valid for a single fill operation (not cached)

## Interfaces (internal)
- `onFillRequest(request: FillRequest): FillResponse` — AutofillService callback
- `onAuthentication(entry: PassEntry): Dataset` — called after user selects + biometric succeeds

## Non-Goals (v1)
- Passkey / FIDO2 support
- Save/update credential flow (read-only)
- Accessibility service fallback
