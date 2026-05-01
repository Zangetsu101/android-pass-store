# PassDroid — Implementation Tasks

## Design Migration (v1.0)

### Phase 1 — Onboarding Restructure

- [ ] Add `gpgImported` flag to `AppPreferences` (DataStore boolean)
- [ ] Update `NavGraph` routing: route to GPG screen if `remoteUrl` empty but `gpgImported` true; save `remoteUrl` only on clone success
- [ ] **Build check** — `assembleDebug` (NavGraph changes are high-risk)
- [ ] Add `WelcomeScreen` — feature tag pills (gpg, git, pass), single CTA `$ clone a store`
- [ ] Merge `OnboardingRemoteUrlScreen` + `OnboardingSshKeyScreen` → `CloneRepoScreen` — URL input + SSH key display with copy/regenerate, step indicator 1/2
- [ ] **Build check** — `assembleDebug` (old screen files deleted, new one added)
- [ ] Reorder onboarding: `Welcome → CloneRepo → GpgImport → CloneProgress`
- [ ] Update `CloneProgressScreen` to terminal-style streaming output + progress bar + cancel button (design 02b)
- [ ] Update `GpgImportScreen` step indicator to show 2/2
- [ ] **Build check** — `assembleDebug`

### Phase 2 — Home Screen Polish

- [ ] Sync state (04b): dim tree to 45% opacity during sync, add status banner showing git command, spinning sync chip
- [ ] Visual pass on tree and flat list views — align spacing, typography, and active entry highlight (`AccentDim` background) to design tokens
- [ ] **Build check** — `assembleDebug`

### Phase 3 — Entry Detail Screen

- [ ] Extract `EntryDetailSheet` from `EntryBrowserScreen` → new `EntryDetailScreen` (full screen)
- [ ] Wire `EntryDetailScreen` into `NavGraph` as a proper destination
- [ ] **Build check** — `assembleDebug` (extraction + NavGraph wiring)
- [ ] Add shimmer decrypting state (06a) — password spinner, notes shimmer skeleton, shimmer copy/reveal buttons
- [ ] Add blur/reveal toggle — 5dp blur on password field, auto-blur after 45s on reveal
- [ ] Add metadata table — path, modified date, commit hash
- [ ] Verify 45s clipboard auto-clear timer is wired correctly in new screen
- [ ] **Build check** — `assembleDebug`

### Phase 4 — Settings Screen

- [ ] Restructure `SettingsScreen` into sections: git, gpg, display, store
- [ ] Git section: remote URL (read-only), SSH key (viewable), last sync timestamp
- [ ] GPG section: key fingerprint (read-only), re-import option
- [ ] Display section: theme toggle placeholder (light theme deferred), display prefs toggles
- [ ] Store section: manual lock, session timeout, clear all data
- [ ] **Build check** — `assembleDebug`

---

## Manual Verification (Final)

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
