# Unit Test Plan

## Stack

Migrate fully from JUnit4 + Mockito-Kotlin → **JUnit5 + MockK + Turbine**.

Dependencies to add: `junit-jupiter`, `android-junit5` plugin (mannodermaus), `mockk-android`, `turbine`  
Dependencies to remove: `junit4`, `mockito-kotlin`

Existing test files (`GitSyncImplTest`, `PassStoreImplTest`, `DecryptionImplTest`) rewritten in JUnit5 + MockK as part of this effort.

No coverage gate yet — add tests first.

## Production Code Changes Required

- **Inject `CoroutineScope`** into `CachingPassphraseProvider` constructor — required for `TestCoroutineScheduler` time control in unit tests.
- **TODO**: verify `file://` protocol actually works end-to-end before treating it as supported.

---

## Test Cases

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

### SessionStartViewModel — 7 tests

Setup: mock `GpgKeyOperations` + mock `SessionOperations` + mock `AppPreferences`. Use `StandardTestDispatcher`.

| #   | Test                                    | Assertion                                              |
| --- | --------------------------------------- | ------------------------------------------------------ |
| 1   | Init with `EndReason.MANUAL`            | `title = "start session"`                              |
| 2   | Init with `EndReason.BIOMETRIC_CHANGED` | `title = "security change detected"`                   |
| 3   | Init with `EndReason.TIMEOUT`           | `title = "session expired"`                            |
| 4   | `sessionTimeoutMinutes` pref changes    | state reflects new value                               |
| 5   | `submit` with empty passphrase          | no state change                                        |
| 6   | `submit` success                        | Turbine: `loading=true` → `success=true`               |
| 7   | `submit` with `WrongPassphrase`         | Turbine: `loading=true` → `error` set, `loading=false` |

---

### EntryDetailViewModel — 10 tests

Setup: mock `Decryption` + mock `GitSync` + mock `AppPreferences` + mock `Context`. `FragmentActivity` passed as `mockk()` (not used beyond delegation to mocked `Decryption`). Mock `ClipboardManager` via Robolectric for `copyPassword` test.

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
