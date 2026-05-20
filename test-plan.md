# Unit Test Plan

## Stack

Migrate fully from JUnit4 + Mockito-Kotlin → **JUnit5 + MockK + Turbine**.

Dependencies to add: `junit-jupiter`, `android-junit5` plugin (mannodermaus), `mockk-android`, `turbine`  
Dependencies to remove: `junit4`, `mockito-kotlin`

Existing test files (`GitSyncImplTest`, `PassStoreImplTest`, `DecryptionImplTest`) rewritten in JUnit5 + MockK as part of this effort.

No coverage gate yet — add tests first.

## Production Code Changes Required

- **Extract `SessionKeyStore` interface** from `SessionManager`: wraps all keystore + blob file ops (`hasSession`, `createKey`, `storeEncryptedPassphrase`, `readEncryptedPassphrase`, `deleteSession`, `getDecryptCipher`). `getDecryptCipher` owns cipher init and may throw `KeyPermanentlyInvalidatedException`. Inject into `SessionManager`.
- **Extract `BiometricPrompter` interface** from `SessionManager`: single `suspend fun prompt(activity, cipher): Cipher`. Inject into `SessionManager`.
- **Inject `CoroutineScope`** into `SessionManager` constructor — required for `TestCoroutineScheduler` time control in unit tests.
- ~~Migration path in `SessionManager.init` (`lastTouched == -1L && hasSession`)~~ — removed.
- **Remove `https://`** from `CloneRepoViewModel.validateRemoteUrl` — accepted by validation but SSH-key-only auth means it silently fails at clone time.
- **TODO**: verify `file://` protocol actually works end-to-end before treating it as supported.

---

## Test Cases

### SessionManager — 11 unit tests

Setup: mock `SessionKeyStore` + mock `BiometricPrompter` + mock `AppPreferences`. Inject `TestScope(TestCoroutineScheduler)`. Use `runTest` + `advanceTimeBy`. Turbine for `sessionState` flow.

| #   | Test                                                                                      | Assertion                                                                |
| --- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 1   | Init: `lastTouched` set + `hasSession=true`, time remaining                               | `sessionState` emits `Active`; timer scheduled for remaining ms          |
| 2   | Init: `lastTouched` set + `hasSession=true`, elapsed > timeout                            | immediate `Inactive(TIMEOUT)`                                            |
| 3   | Init: `lastTouched = -1` or `hasSession=false`                                            | stays `Inactive(MANUAL)`                                                 |
| 4   | `createSession` passphrase > 190 bytes                                                    | throws `PassphraseTooLong`, state unchanged                              |
| 5   | `createSession` success                                                                   | `sessionState` emits `Active`; `keyStore.createKey` + `store` called     |
| 6   | `endSession(MANUAL)`                                                                      | `sessionState` emits `Inactive(MANUAL)`; `keyStore.deleteSession` called |
| 7   | `touchSession` → `advanceTimeBy(timeoutMs)`                                               | `sessionState` emits `Inactive(TIMEOUT)`                                 |
| 8   | `touchSession` called before deadline, then `advanceTimeBy(timeoutMs)`                    | timer resets; no early expiry                                            |
| 9   | `touchSession` with `timeoutMs = 0`                                                       | no timer launched, session stays `Active` indefinitely                   |
| 10  | `getPassphrase` when `Inactive`                                                           | throws `NoActiveSession`                                                 |
| 11  | `getPassphrase` → `keyStore.getDecryptCipher` throws `KeyPermanentlyInvalidatedException` | `endSession(BIOMETRIC_CHANGED)` called; throws `NoActiveSession`         |

---

### SessionManager — 1 instrumented test

Setup: real `AndroidKeyStore` impl of `SessionKeyStore`; mock `BiometricPrompter` returns pre-initialized cipher.

| #   | Test                                                    | Assertion                     |
| --- | ------------------------------------------------------- | ----------------------------- |
| 1   | `createSession(passphrase)` → `getPassphrase(activity)` | returns original `passphrase` |

---

### CachingPassphraseProvider — 4 unit tests

Setup: mock `PassphraseProvider` (delegate) + mock `SessionOperations`. Inject `TestScope(TestCoroutineScheduler)`. Use `runTest` + `advanceTimeBy`.

| #   | Test                                            | Assertion                                                         |
| --- | ----------------------------------------------- | ----------------------------------------------------------------- |
| 1   | Cache miss: first call                          | delegates to provider; result cached; `touchSession` called       |
| 2   | Cache hit: second call before 5 min             | cached value returned; delegate NOT called; `touchSession` called |
| 3   | Cache expires: `advanceTimeBy(5 min)` then call | delegate called again                                             |
| 4   | `sessionState` emits `Inactive`                 | `cachedPassphrase` cleared immediately                            |

---

### CryptoService — 16 tests

Setup: mock `SessionOperations` + mock `KeyBlobStore`. Real PGPainless (generate test keys inline, consistent with `DecryptionImplTest` pattern). Temp `filesDir` for GPG key file.

**importGpgKey (3)**
| # | Test | Assertion |
|---|------|-----------|
| 1 | Valid passphrase-protected armored key | File written, no exception |
| 2 | Key with no passphrase (`s2KUsage == 0`) | throws `KeyImportError.NoPassphrase` |
| 3 | Malformed / non-PGP text | throws `KeyImportError.Malformed` |

**startSession (4)**
| # | Test | Assertion |
|---|------|-----------|
| 4 | Correct passphrase | `sessionOperations.createSession` called |
| 5 | Passphrase > 190 bytes | throws `SessionError.PassphraseTooLong` |
| 6 | Wrong passphrase | throws `SessionError.WrongPassphrase` |
| 7 | No GPG file on disk | throws `IllegalStateException` |

**getGpgKey (3)**
| # | Test | Assertion |
|---|------|-----------|
| 8 | Passphrase cached | returns key, `sessionOperations.getPassphrase` NOT called |
| 9 | No cache, session active | calls `getPassphrase`, caches result |
| 10 | Session inactive | throws `SessionError.NoActiveSession` |

**Passphrase cache lifecycle (2)**
| # | Test | Assertion |
|---|------|-----------|
| 11 | `sessionState` emits `Inactive` | `cachedPassphrase` cleared immediately |
| 12 | `advanceTimeBy(BIOMETRIC_CACHE_TIMEOUT_MS)` | cache cleared, next `getGpgKey` calls `getPassphrase` again |

**Key info / management (3)**
| # | Test | Assertion |
|---|------|-----------|
| 13 | `getGpgKeyInfo` — no file | returns `null` |
| 14 | `getGpgKeyInfo` — valid key | returns `(keyId, uid)` pair |
| 15 | `clearAllKeys` | `endSession` called + `blobStore.deleteAll` called |

**SSH round-trip (1)**
| # | Test | Assertion |
|---|------|-----------|
| 16 | `generateSshKey` then `getSshKey` | returned `KeyPair` reconstructed from stored blobs |

---

### AppPreferences — ~9 tests

Setup: `PreferenceDataStoreFactory.create()` with temp file in `TestScope`. No mocking needed.

| #   | Test                                          | Assertion                                                                                                                                 |
| --- | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 1–6 | Default value for each of 6 prefs (no writes) | `remoteUrl=""`, `sessionTimeoutMinutes=5`, `sshPublicKey=null`, `gpgImported=false`, `clipboardTimeoutSeconds=45`, `defaultViewTree=true` |
| 7   | Round-trip: write + read each pref            | value survives DataStore serialization                                                                                                    |
| 8   | `clearRemoteUrl`                              | `remoteUrl` flow emits `""`                                                                                                               |
| 9   | `clearAll`                                    | all flows emit defaults                                                                                                                   |

---

### SessionStartViewModel — 7 tests

Setup: mock `CryptoOperations` + mock `SessionOperations` + mock `AppPreferences`. Use `StandardTestDispatcher`.

| #   | Test                                    | Assertion                                              |
| --- | --------------------------------------- | ------------------------------------------------------ |
| 1   | Init with `EndReason.REBOOT`            | `title = "start session"`                              |
| 2   | Init with `EndReason.BIOMETRIC_CHANGED` | `title = "security change detected"`                   |
| 3   | Init with `EndReason.TIMEOUT`           | `title = "session expired"`                            |
| 4   | `sessionTimeoutMinutes` pref changes    | state reflects new value                               |
| 5   | `submit` with empty passphrase          | no state change                                        |
| 6   | `submit` success                        | Turbine: `loading=true` → `success=true`               |
| 7   | `submit` with `WrongPassphrase`         | Turbine: `loading=true` → `error` set, `loading=false` |

---

### GpgImportViewModel — 4 tests

Setup: mock `CryptoOperations` + mock `AppPreferences`.

| #   | Test                            | Assertion                                                             |
| --- | ------------------------------- | --------------------------------------------------------------------- |
| 1   | `setGpgKeyText("foo")`          | state: `gpgKeyText="foo"`, `gpgImportError=null`, `gpgImported=false` |
| 2   | `importGpgKey` success          | `gpgImported=true`, `appPreferences.setGpgImported(true)` called      |
| 3   | `importGpgKey` → `NoPassphrase` | `gpgImportError` set, `gpgImported=false`                             |
| 4   | `importGpgKey` → `Malformed`    | `gpgImportError` set                                                  |

---

### CloneRepoViewModel — 7 tests

Setup: mock `CryptoOperations` + mock `AppPreferences`.

| #   | Test                                             | Assertion                                                                            |
| --- | ------------------------------------------------ | ------------------------------------------------------------------------------------ |
| 1   | Init                                             | SSH key generated, `state.sshPublicKey` set, `appPreferences.setSshPublicKey` called |
| 2   | `validateRemoteUrl` empty string                 | error "Remote URL is required", returns `false`                                      |
| 3   | `validateRemoteUrl` invalid (e.g. `"ftp://..."`) | error "Enter a valid git remote URL", returns `false`                                |
| 4   | `validateRemoteUrl("git@github.com:x/y.git")`    | `remoteUrlError=null`, returns `true`                                                |
| 5   | `validateRemoteUrl("ssh://git@github.com/x/y")`  | valid, returns `true`                                                                |
| 6   | `validateRemoteUrl("file:///local/repo")`        | valid, returns `true` _(TODO: confirm file:// works end-to-end)_                     |
| 7   | `regenerateSshKey`                               | new key generated, prefs updated                                                     |

---

### SyncPanelViewModel — 4 tests

Setup: mock `GitSync` + mock `PassStore`.

| #   | Test                                    | Assertion                                                                   |
| --- | --------------------------------------- | --------------------------------------------------------------------------- |
| 1   | Init                                    | `remoteReachable` + `lastSyncTime` populated from `syncStatus()`            |
| 2   | `loadStatus` when `syncStatus()` throws | `remoteReachable=false`, no crash                                           |
| 3   | `pull` success                          | `passStore.buildIndex()` called, `pullSuccess=true`, `lastSyncTime` updated |
| 4   | `pull` failure                          | `pullError` set, `loadStatus()` called again                                |

---

### EntryDetailViewModel — 10 tests

Setup: mock `Decryption` + mock `GitSync` + mock `CryptoOperations` + mock `AppPreferences`. `FragmentActivity` passed as `mockk()` (not used beyond delegation to mocked `Decryption`). Mock `ClipboardManager` via Robolectric for `copyPassword` test.

| #   | Test                                            | Assertion                                                                            |
| --- | ----------------------------------------------- | ------------------------------------------------------------------------------------ |
| 1   | Init — `lastCommitForFile` returns info         | `gitStatus = Tracked(commitInfo)`                                                    |
| 2   | Init — `lastCommitForFile` returns `null`       | `gitStatus = Untracked`                                                              |
| 3   | Init — `clipboardTimeoutSeconds` pref           | state reflects value                                                                 |
| 4   | `authenticate` when already `Decrypting`        | no-op, state unchanged                                                               |
| 5   | `authenticate` success                          | `unlockState = Decrypted(credentials)`                                               |
| 6   | `authenticate` → `BiometricAuthException`       | `unlockState = Idle`                                                                 |
| 7   | `authenticate` → `SessionError.NoActiveSession` | `unlockState = Failed("Session expired — return to home and unlock")`                |
| 8   | `authenticate` → `DecryptionError`              | `unlockState = Failed(message)`                                                      |
| 9   | `toggleReveal` when `Decrypted`                 | `passwordRevealed` toggled                                                           |
| 10  | `copyPassword`                                  | Turbine: `clipboardCopied=true` → `advanceTimeBy(timeout)` → `clipboardCopied=false` |

---

### EntryBrowserViewModel + SettingsViewModel

_Test cases not yet drilled — to be decided before implementation._
