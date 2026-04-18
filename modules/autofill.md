# Autofill Module

## Implementation
- **AutofillService:** `android.service.autofill.AutofillService` — `@AndroidEntryPoint` (Hilt), `minSdk` 26
- **Credential Manager:** `androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth`, gated behind `Build.VERSION.SDK_INT >= 34`
- **`BiometricPromptData` integration:** gated behind `Build.VERSION.SDK_INT >= 36`
- **Async:** Coroutines via `lifecycleScope` on the service; fill requests handled on `Dispatchers.Default`

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
- **Android 16+:** `BiometricPromptData` can be embedded directly into the Credential Manager `GetCredentialRequest`, replacing the separate `IntentSender` round-trip for the Credential Manager path. The `AutofillService` path is unchanged.
- **Android 16+ Identity Check:** When the device is outside a user-defined trusted location, the OS enforces biometric-only auth for credential access — PIN/password fallback is disabled at the platform level. Our per-fill biometric requirement aligns with this automatically.

## Interfaces (internal)
- `onFillRequest(request: FillRequest): FillResponse` — AutofillService callback
- `onAuthentication(entry: PassEntry): Dataset` — called after user selects + biometric succeeds

## Acceptance Checklist

```
[manual] PassDroid appears as option in Android Settings → Autofill service
[manual] autofill suggestions appear in Chrome browser login form
[manual] autofill suggestions appear in a native app login form
           (test with at least one app matching a pass entry by package name)
[manual] suggestion list is ranked (exact match above fuzzy)
[manual] selecting a suggestion triggers biometric prompt
[manual] correct username + password filled after biometric success
[manual] no fill occurs if biometric cancelled or failed
[manual] autofill works with device screen locked then unlocked (cold service start)
[manual] on Android 14+: Credential Manager bottom sheet appears instead of dropdown
[manual] on Android 16+: BiometricPromptData used (no separate IntentSender round-trip)
[manual] on Android 16+: biometric-only enforced outside trusted location (Identity Check)
```

## Non-Goals (v1)
- Passkey / FIDO2 support
- Save/update credential flow (read-only)
- Accessibility service fallback
