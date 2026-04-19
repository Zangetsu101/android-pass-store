# PassDroid — Implementation Tasks

Tasks are ordered by dependency. Each task is complete when its automated tests pass.
Manual verification is consolidated at the end after full implementation.

---

## 1. Project Scaffold
- [x] Create Android project (Kotlin, minSdk 26, targetSdk 36)
- [x] Configure `build.gradle.kts` with all dependencies:
      JGit, MINA sshd, PGPainless, Hilt, Navigation Compose,
      DataStore, BiometricPrompt, Credentials, Material3
- [x] Set up `@HiltAndroidApp` application class
- [x] Set up empty `NavHost` with placeholder destinations
- [x] Confirm project builds and empty app launches

**Commit:** `chore: project scaffold with all dependencies`

---

## 2. Key Management — Storage Infrastructure
- [x] Define `KeyManagement` interface
- [x] Implement Keystore wrapping key generation (AES-256-GCM)
- [x] Implement encrypt/decrypt blob helpers (app-private storage)
- [x] Implement `clearAllKeys()` — wipes blobs + Keystore entries
- [x] Hilt `@Singleton` binding

**Commit:** `feat: keystore-backed key blob storage infrastructure`

---

## 3. Key Management — GPG Key Import
- [x] Implement `importGpgKey(armoredKey, passphrase?)`
        — parse armored key, decrypt if passphrase given, re-encrypt under wrapping key
- [x] Implement `getGpgKey(): GpgPrivateKey` (unwrap blob, return in-memory key)
- [x] Reject malformed armored key with `KeyImportError`

**Commit:** `feat: GPG key import and retrieval`

---

## 4. Key Management — SSH Key Generation
- [x] Implement `generateSshKey(): PublicKey` — Ed25519, store encrypted blob
- [x] Implement `getSshKey(): SshPrivateKey` (unwrap blob, return in-memory key)
- [x] Format public key as OpenSSH (`ssh-ed25519 AAAA...`)

**Commit:** `feat: SSH key generation and retrieval`

---

## 5. Key Management — Session + Biometric
- [x] Wire `BiometricPrompt` into `getGpgKey()` and `getSshKey()`
- [x] Implement session inactivity timer (default 5 min, configurable via DataStore)
- [x] Degrade gracefully when biometric not enrolled (fall back to device PIN)

**Commit:** `feat: biometric unlock and session timeout`

---

## 6. Git Sync Module
- [x] Define `GitSync` interface + `SyncResult`, `SyncStatus`, `SyncError` sealed types
- [x] Implement `clone(remoteUrl, localPath)` via JGit (SSH stubbed — wired in task 14)
- [x] Implement `pull()` — fast-forward only (`--ff-only`), return `SyncResult`
- [x] Implement `syncStatus()` — last sync time, remote reachable check
- [x] Hilt `@Singleton` binding, all ops on `Dispatchers.IO`
- [x] Add test fixtures: local bare git repo with sample `.gpg` files

**Tests:**
- [x] clone() creates working copy from `file://` bare repo
- [x] pull() returns correct newEntries / removedEntries
- [x] pull() with no changes returns empty SyncResult
- [x] pull() returns `SyncError.NotFastForward` when remote diverged
- [x] syncStatus() reflects last pull timestamp
- [x] syncStatus() returns `RemoteUnreachable` for invalid path

**Commit:** `feat: git sync module (JGit + MINA sshd)`

---

## 7. Pass Store Module
- [x] Define `PassStore` interface + `PassEntry` data class
- [x] Implement `buildIndex()` — walk git working copy, parse all `.gpg` paths
- [x] Implement path parsing: `web/github.com/alice.gpg` → domain + username
- [x] Implement `resolve(domain)` — exact → subdomain → fuzzy (Levenshtein)
- [x] Implement `resolve(packageName)` — strip prefix, map to domain
- [x] Implement `search(query)` — case-insensitive ranked search
- [x] Expose index as `StateFlow<List<PassEntry>>`
- [x] Hilt `@Singleton` binding

**Tests:**
- [x] buildIndex() parses `web/github.com/alice.gpg` → domain=github.com, username=alice
- [x] buildIndex() handles 1-level path → domain=null
- [x] buildIndex() ignores non-.gpg files
- [x] resolve(domain) exact match returns entry first
- [x] resolve(domain) subdomain: github.com resolves gist.github.com query
- [x] resolve(domain) fuzzy: "githubb.com" returns github.com entry
- [x] resolve(domain) returns empty list when no candidates
- [x] resolve(packageName) maps com.github.android → github.com entry
- [x] search() is case-insensitive
- [x] search() ranks closer matches higher

**Commit:** `feat: pass store module (index, resolve, fuzzy search)`

---

## 8. Decryption Module
- [x] Define `Decryption` interface + `Credentials`, `AutofillCredentials` types
        (`password: CharArray`, `notes: String`)
- [x] Add test resources: throwaway GPG keypair + pre-encrypted fixture `.gpg` files
        (single-line, multi-line, wrong-key)
- [x] Implement `decrypt(entry)` via PGPainless — calls `KeyManagement.getGpgKey()`
- [x] Implement `decryptForAutofill(entry)` — password only, no notes
- [x] Implement `CharArray.zero()` extension, call after use
- [x] Hilt `@Singleton` binding, ops on `Dispatchers.IO`

**Tests:**
- [x] decrypt() returns password from line 1 (single-line fixture)
- [x] decrypt() returns password + notes (multi-line fixture)
- [x] decrypt() throws DecryptionError for wrong-key fixture
- [x] decrypt() throws DecryptionError for corrupted file
- [x] decryptForAutofill() returns only password
- [x] CharArray is all-zero after explicit clear call

**Commit:** `feat: decryption module (PGPainless + CharArray zeroing)`

---

## 9. Onboarding UI
- [ ] Define Navigation Compose graph — all routes type-safe via `@Serializable`
- [ ] Onboarding detection: navigate to onboarding if no git remote in DataStore
- [ ] Screen 1: git remote URL input + validation
- [ ] Screen 2: SSH key generation → display copyable public key
- [ ] Screen 3: GPG key import — paste textarea + file picker
- [ ] Screen 4: biometric enrollment prompt
- [ ] Screen 5: initial clone with progress indicator + error/retry

**Commit:** `feat: onboarding flow`

---

## 10. Entry Browser UI
- [ ] Entry list screen: `StateFlow<List<PassEntry>>` → lazy column
- [ ] Real-time search bar wired to `PassStore.search()`
- [ ] Entry detail: biometric prompt → `Decryption.decrypt()` → show password + notes
- [ ] Copy button → clipboard → `delay(45_000)` → `clearPrimaryClip()`
- [ ] Directory tree toggle (flat vs tree view)

**Commit:** `feat: entry browser with search and clipboard`

---

## 11. Sync Panel + Settings UI
- [ ] Sync panel: last sync time from DataStore, manual pull button, error display
- [ ] Settings:
      — autofill toggle (deep-link to `ACTION_REQUEST_SET_AUTOFILL_SERVICE`)
      — session timeout picker (wired to DataStore)
      — SSH public key display
      — "Clear all data" → `KeyManagement.clearAllKeys()` + wipe DataStore + delete repo → onboarding

**Commit:** `feat: sync panel and settings`

---

## 12. AutofillService
- [ ] Declare `PassDroidAutofillService` in `AndroidManifest.xml`
        with `<meta-data android:name="android.autofill" android:resource="@xml/autofill_service"/>`
- [ ] Implement `onFillRequest()` — `PassStore.resolve()` → `FillResponse` with locked datasets
- [ ] Implement auth `IntentSender` activity — biometric → `Decryption.decryptForAutofill()` → `Dataset`
- [ ] Return null `FillResponse` when no candidates found

**Commit:** `feat: autofill service`

---

## 13. Credential Manager Provider (Android 14+)
- [ ] Declare `PassDroidCredentialProviderService` in manifest
- [ ] Implement `onBeginGetCredentialRequest()` → resolve candidates
- [ ] Implement `onBeginCreateCredentialRequest()` → no-op (read-only)
- [ ] On Android 16+: use `BiometricPromptData` in `GetCredentialRequest`

**Commit:** `feat: credential manager provider (Android 14+, BiometricPromptData on 16+)`

---

## 14. Integration + Hardening
- [ ] Audit all `Dispatchers` usage — no blocking calls on `Main`
- [ ] Audit manifest permissions — request only what's needed
- [ ] Verify ProGuard/R8 rules don't strip JGit, PGPainless, MINA sshd reflection paths
- [ ] Verify `android:allowBackup="false"` in manifest (no key material in backups)
- [ ] Review all `CharArray` zero paths — no dangling decrypted data
- [ ] Wire Apache MINA sshd SSH transport into `GitSync.clone()` / `pull()`

**Commit:** `chore: integration hardening and manifest audit`

---

## 15. Manual Verification (Final)

Run once after task 14 is complete. Sign off each item before shipping.

### Key Management
- [ ] importGpgKey() accepts armored key with passphrase
- [ ] importGpgKey() accepts armored key without passphrase
- [ ] importGpgKey() rejects malformed armored key with clear error
- [ ] imported GPG key survives app restart
- [ ] generateSshKey() returns valid Ed25519 public key in OpenSSH format
- [ ] generated SSH key survives app restart
- [ ] getGpgKey() requires biometric when session is locked
- [ ] getGpgKey() skips biometric within active session window
- [ ] keys inaccessible after session timeout (app re-prompts biometric)
- [ ] clearAllKeys() removes key blobs and Keystore entries
- [ ] app handles biometric not enrolled — degrades to device PIN gracefully

### Git Sync
- [ ] clone() succeeds over real SSH remote with generated keypair
- [ ] pull() picks up entry added on Linux and pushed
- [ ] SSH auth failure surfaces actionable error in UI

### Decryption
- [ ] decrypt() prompts biometric when session locked
- [ ] decrypt() skips biometric within active session
- [ ] no decrypted content in `adb shell run-as <pkg> ls files/`

### Onboarding
- [ ] fresh install lands on onboarding (not browser)
- [ ] SSH public key displayed in copyable format
- [ ] GPG import works via paste
- [ ] GPG import works via file picker
- [ ] progress indicator shown during clone
- [ ] bad URL → error + retry option
- [ ] completing onboarding → entry browser

### Entry Browser
- [ ] all entries visible after sync, directory structure preserved
- [ ] search filters in real time, case-insensitive
- [ ] tapping entry triggers biometric
- [ ] password + notes shown after biometric success
- [ ] clipboard cleared after 45 seconds

### Sync Panel + Settings
- [ ] last sync timestamp updates after pull
- [ ] manual pull refreshes entry list
- [ ] network error shows last-known-good timestamp
- [ ] SSH auth failure shows link to SSH key screen
- [ ] autofill toggle opens system autofill settings
- [ ] session timeout change takes effect immediately
- [ ] SSH key viewable for re-registration
- [ ] "Clear all data" → onboarding, no keys/repo/prefs remain

### AutofillService
- [ ] PassDroid appears in Android Settings → Autofill service
- [ ] suggestions appear in Chrome browser login form
- [ ] suggestions appear in a native app login form
- [ ] suggestions ranked (exact above fuzzy)
- [ ] selecting suggestion triggers biometric
- [ ] correct username + password filled after success
- [ ] no fill if biometric cancelled or failed
- [ ] autofill works after cold service start (screen lock/unlock)

### Credential Manager (Android 14+)
- [ ] Credential Manager bottom sheet appears on Android 14+
- [ ] correct credentials filled via bottom sheet
- [ ] on Android 16+: no separate auth activity launched (BiometricPromptData path)
- [ ] on Android 16+: biometric-only enforced outside trusted location (Identity Check)

---

## Dependency Order

```
1 → 2 → 3 → 4 → 5        (Key Management, bottom-up)
            5 → 6         (SSH key needed for Git Sync)
            6 → 7         (git working copy needed for Pass Store)
        3 → 8             (GPG key needed for Decryption)
    7 + 8 → 9 → 10 → 11  (UI builds on all modules)
    7 + 8 → 12            (Autofill parallel to UI)
       12 → 13            (Credential Manager wraps Autofill)
       11 + 13 → 14 → 15  (Hardening then manual verification last)
```
