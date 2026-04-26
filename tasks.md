# PassDroid — Implementation Tasks

Tasks are ordered by dependency. Each task is complete when its automated tests pass.
Manual verification is consolidated at the end after full implementation.

> **Note:** Tasks 1–5 intentionally leave the build broken. The build is restored in task 6 (caller update).

---

## 1. Key Management — Interface Redesign

> ⚠️ Build broken after this task until task 6.

Rework `KeyManagement` to match the new session model. Previous implementation (Keystore-wrapped blobs for GPG) is replaced.

- [ ] Redefine `KeyManagement` interface:
      `importGpgKey(armoredKey: String)`
      `startSession(passphrase: String)`
      `endSession()`
      `isSessionActive(): Boolean`
      `getGpgKey(biometricPrompt): GpgPrivateKey`
      `generateSshKey(): PublicKey`
      `getSshKey(): SshPrivateKey`
      `clearAllKeys()`
- [ ] Update Hilt `@Singleton` binding

**Commit:** `refactor: redefine KeyManagement interface for new session model`

---

## 2. Key Management — GPG Key Import

> ⚠️ Build broken until task 6.

- [ ] Implement `importGpgKey(armoredKey: String)`:
      — parse armored key structure, reject if passphrase-unprotected (throw `KeyImportError.NoPassphrase`)
      — reject malformed key (throw `KeyImportError.Malformed`)
      — store armored blob as-is to `files/keys/gpg.asc`
- [ ] Implement `isGpgKeyImported(): Boolean` — check file exists

**Commit:** `feat: GPG key import (passphrase-protected only, store armored as-is)`

---

## 3. Key Management — SSH Key

> ⚠️ Build broken until task 6.

- [ ] Implement `generateSshKey(): PublicKey` — Ed25519, encrypt blob under **device-unlock-bound** Keystore key (no biometric required)
- [ ] Implement `getSshKey(): SshPrivateKey` — unwrap from Keystore, no biometric prompt
- [ ] Format public key as OpenSSH (`ssh-ed25519 AAAA...`)
- [ ] Update `clearAllKeys()` to delete SSH blob + Keystore entry

**Commit:** `feat: SSH key generation (device-unlock-bound, no biometric)`

---

## 4. Key Management — Session

> ⚠️ Build broken until task 6.

- [ ] Implement `startSession(passphrase: String)`:
      — attempt to decrypt armored key with passphrase; throw `SessionError.WrongPassphrase` on failure
      — on success: store passphrase encrypted under **biometric-bound** Keystore key (`passdroid_session_key`)
- [ ] Implement `endSession()` — delete `passdroid_session_key` Keystore entry
- [ ] Implement `isSessionActive(): Boolean` — check `passdroid_session_key` exists in Keystore
- [ ] Implement inactivity timeout:
      — DataStore-configurable duration (default 5 min)
      — timer resets on each `getGpgKey()` call
      — on expiry: call `endSession()`
- [ ] Register `BroadcastReceiver` for `ACTION_BOOT_COMPLETED` → call `endSession()`
- [ ] Update `clearAllKeys()` to call `endSession()` first

**Commit:** `feat: session lifecycle (biometric-bound passphrase, inactivity timeout, boot receiver)`

---

## 5. Key Management — GPG Key Access

> ⚠️ Build broken until task 6.

- [ ] Implement `getGpgKey(biometricPrompt: BiometricPrompt): GpgPrivateKey`:
      — throw `SessionError.NoActiveSession` if `isSessionActive()` is false
      — show biometric prompt with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` fallback
      — on success: unwrap passphrase from Keystore → decrypt armored key → return in-memory key
      — reset inactivity timer

**Commit:** `feat: GPG key access (biometric → passphrase → decrypt armored key)`

---

## 6. Update All KeyManagement Callers

Restores the build after tasks 1–5. Update every caller of the old `KeyManagement` interface across the codebase.

- [ ] Update `DecryptionModule` — replace old `getGpgKey()` call with new signature
- [ ] Update onboarding ViewModel — replace `importGpgKey(armoredKey, passphrase?)` with `importGpgKey(armoredKey)`
- [ ] Update entry browser ViewModel — replace old `getGpgKey()` call
- [ ] Update autofill `IntentSender` activity — replace old `getGpgKey()` call
- [ ] Update settings ViewModel — replace old `clearAllKeys()` call if signature changed
- [ ] Verify `./gradlew assembleDebug` passes cleanly

**Commit:** `refactor: update all KeyManagement callers to new interface`

---

## 7. Onboarding — Remove Biometric Enrollment Screen

Session-start is not part of onboarding — it happens lazily when the user first tries to decrypt an entry.

- [ ] Remove all navigation calls to the biometric enrollment route **before** deleting the screen
- [ ] Delete the biometric enrollment screen composable and route
- [ ] Verify `./gradlew assembleDebug` passes cleanly

**Commit:** `refactor: remove biometric enrollment screen from onboarding`

---

## 8. Session Start Screen

Standalone screen used by both the entry browser (lazy session-start) and autofill redirect.

- [ ] Implement `SessionStartScreen` — passphrase input field, submit button
- [ ] On submit: call `startSession(passphrase)`, show inline error on `SessionError.WrongPassphrase`
- [ ] On success: pop back to the previous destination (entry detail or wherever the redirect came from)
- [ ] Add `SessionStart` route to the NavGraph

**Commit:** `feat: session start screen`

---

## 9. Entry Browser — Lazy Session Start

- [ ] In entry detail ViewModel, catch `SessionError.NoActiveSession` from `getGpgKey()`
- [ ] On catch: navigate to `SessionStartScreen`
- [ ] On return from `SessionStartScreen` (session now active): re-attempt `getGpgKey()` → biometric prompt → show decrypted entry

**Commit:** `feat: entry browser lazy session-start on inactive session`

---

## 10. AutofillService — Session State Check

Candidates are always shown (not locked). Tapping a candidate when session is inactive redirects to session-start instead of showing biometric.

- [ ] Show all resolved candidates unconditionally in `FillResponse` (no locked datasets)
- [ ] Each dataset's `IntentSender` activity checks `isSessionActive()`:
      — if active: show biometric prompt → `decryptForAutofill()` → return `Dataset` result
      — if inactive: launch `SessionStartActivity` in the main app with a `PendingIntent`;
      after passphrase entry succeeds, finish and return to the originating app —
      user triggers autofill again (session now active, normal biometric flow)

**Commit:** `feat: autofill session state check (inactive → session-start redirect)`

---

## 11. Settings — Session Controls

- [ ] Add **manual lock** button → calls `endSession()`
- [ ] Session timeout setting: toggle between inactivity timeout (with duration picker) and manual-lock-only mode
- [ ] Both settings wired to DataStore, take effect immediately
- [ ] Verify `./gradlew assembleDebug` passes cleanly

**Commit:** `feat: session settings (manual lock, timeout mode)`

---

## 12. Manual Verification (Final)

Run once after task 11 is complete. Sign off each item before shipping.

### Key Management

- [ ] importGpgKey() accepts passphrase-protected armored key
- [ ] importGpgKey() rejects unprotected armored key with clear error
- [ ] importGpgKey() rejects malformed armored key with clear error
- [ ] imported GPG key survives app restart (blob persists)
- [ ] startSession() succeeds with correct passphrase
- [ ] startSession() fails with incorrect passphrase, shows clear error
- [ ] generateSshKey() returns valid Ed25519 public key in OpenSSH format
- [ ] generated SSH key survives app restart
- [ ] getSshKey() accessible without biometric after device unlock
- [ ] getGpgKey() requires biometric when session is active
- [ ] getGpgKey() falls back to device PIN when biometric unavailable
- [ ] getGpgKey() throws when session is inactive
- [ ] session ends after inactivity timeout (passphrase required on next use)
- [ ] manual lock immediately ends session
- [ ] device reboot ends session (passphrase required on next open)
- [ ] clearAllKeys() removes both key blobs from app-private storage
- [ ] clearAllKeys() removes all Keystore entries

### Git Sync

- [ ] clone() succeeds over real SSH remote with generated keypair
- [ ] pull() picks up entry added on Linux and pushed
- [ ] SSH auth failure surfaces actionable error in UI

### Decryption

- [ ] decrypt() prompts biometric when session active
- [ ] decrypt() navigates to session-start screen when session inactive
- [ ] no decrypted content in `adb shell run-as <pkg> ls files/`

### Onboarding

- [ ] fresh install lands on onboarding (not browser)
- [ ] SSH public key displayed in copyable format
- [ ] GPG import works via paste
- [ ] GPG import works via file picker
- [ ] unprotected GPG key rejected at import with clear error
- [ ] progress indicator shown during clone
- [ ] bad URL → error + retry option
- [ ] completing onboarding → entry browser

### Entry Browser

- [ ] all entries visible after sync, directory structure preserved
- [ ] search filters in real time, case-insensitive
- [ ] tapping entry triggers biometric (session active)
- [ ] tapping entry navigates to session-start screen (session inactive — e.g. after timeout)
- [ ] after passphrase entry on session-start screen, returns to entry and triggers biometric
- [ ] password + notes shown after biometric success
- [ ] clipboard cleared after 45 seconds

### Sync Panel + Settings

- [ ] last sync timestamp updates after pull
- [ ] manual pull refreshes entry list
- [ ] network error shows last-known-good timestamp
- [ ] SSH auth failure shows link to SSH key screen
- [ ] autofill toggle opens system autofill settings
- [ ] session timeout change takes effect immediately
- [ ] manual lock ends session immediately
- [ ] SSH key viewable for re-registration
- [ ] "Clear all data" → onboarding, no keys/repo/prefs remain

### AutofillService

- [ ] PassDroid appears in Android Settings → Autofill service
- [ ] suggestions appear in Chrome browser login form
- [ ] suggestions appear in a native app login form
- [ ] suggestions ranked (exact above fuzzy)
- [ ] selecting suggestion triggers biometric (session active)
- [ ] selecting suggestion launches SessionStartActivity (session inactive)
- [ ] after session-start, re-triggering autofill proceeds with biometric prompt
- [ ] correct username + password filled after biometric success
- [ ] no fill if biometric cancelled or failed

### Credential Manager (Android 14+)

- [ ] Credential Manager bottom sheet appears on Android 14+
- [ ] correct credentials filled via bottom sheet
- [ ] on Android 16+: no separate auth activity launched (BiometricPromptData path)
- [ ] on Android 16+: biometric-only enforced outside trusted location (Identity Check)

---

## Dependency Order

```
1 → 2 → 3 → 4 → 5 → 6   (Key Management + caller update, build restored at 6)
                  6 → 7   (remove biometric screen after callers updated)
                  7 → 8   (SessionStartScreen added to NavGraph)
                  8 → 9   (entry browser lazy session-start needs SessionStartScreen)
                  8 → 10  (autofill redirect needs SessionStartScreen, parallel to 9)
                  11      (settings session controls, parallel to 9-10)
    9 + 10 + 11 → 12      (manual verification last)
```
