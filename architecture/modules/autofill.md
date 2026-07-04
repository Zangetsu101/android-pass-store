# Autofill Module

## Implementation

- **AutofillService:** `android.service.autofill.AutofillService`, `@AndroidEntryPoint`, available from Android 8+.
- **Inline suggestions:** Android 11+ via `androidx.autofill` when inline presentation specs are supplied by the system.
- **Credential Manager provider:** `androidx.credentials.provider.CredentialProviderService`, gated behind Android 14+.
- **Authentication handoff:** locked/authed candidates use `PendingIntent` activities; decryption happens only after user selection.

`BiometricPromptData` is future work and is not part of the current Credential Manager flow.

## Responsibility

Expose pass credentials to Android **External Credential Surfaces**: AutofillService and Credential Manager. External fill surfaces require fresh biometric confirmation before releasing a secret to another app.

## Components

**AutofillService** (`PassAndroidAutofillService`)

- Receives fill requests with app package name and, for web forms, web domain.
- Detects login fields and credit-card fields from autofill hints / input metadata.
- Calls `PassStore.resolve()` or `resolveByPackage()` for login candidates; no decryption during matching.
- Lists all indexed **Card Entries** for card forms.
- Returns locked datasets using `Dataset.setAuthentication(...)`.
- Includes a `Search...` login dataset for explicit user selection when automatic matching is insufficient.

**Autofill auth/search activities**

- Resolve the selected indexed path back to a `PassEntry`.
- Use the autofill-qualified decryption path, which bypasses the app UI passphrase cache.
- If no active session exists, render the session-start screen in-place; after session start, retry decryption.
- Return a filled `Dataset` to Android or cancel on auth/decryption failure.

**Credential Manager Provider** (`PassAndroidCredentialProviderService`, Android 14+)

- Handles password credential requests.
- Resolves native app candidates from package-name matching.
- Returns `PasswordCredentialEntry` values backed by `PendingIntent` auth.
- The auth activity uses the same fresh-biometric autofill decryption path before returning `PasswordCredential`.
- If no active session exists, the auth activity renders the session-start screen in-place; after session start, it retries decryption and returns to the Credential Manager flow.

## Matching and Fill Semantics

- Automatic login matching is conservative: exact web domain, DNS subdomain suffix, or native package-name reversal heuristic.
- Fuzzy matching is not used for automatic external suggestions; it is available only through explicit user search.
- Login fill writes username from the path-derived entry username and password from line 1 of decrypted content.
- Card fill writes card number from line 1 and card fields (`cvv`, `expiry`, `cardholder`) from notes; selecting a card fills all detected card fields as a unit.

## Session / Biometric Gate

- External credential surfaces use the direct passphrase path: every selected fill requires `BiometricPrompt` before the session passphrase is unwrapped.
- The app UI may use the cached passphrase during an active session; external fill surfaces may not.
- Android 16 Identity Check may further restrict platform credential access outside trusted locations; the app's fresh-biometric external-fill rule aligns with that model.

## Non-Goals (v1)

- Passkey / FIDO2 support
- Save/update credential flow
- Accessibility service fallback
- Credential Manager `BiometricPromptData` inline biometric
