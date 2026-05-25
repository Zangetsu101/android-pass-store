# Unit Test Plan

## Stack

Migrate fully from JUnit4 + Mockito-Kotlin → **JUnit5 + MockK + Turbine**.

Dependencies to add: `junit-jupiter`, `android-junit5` plugin (mannodermaus), `mockk-android`, `turbine`  
Dependencies to remove: `junit4`, `mockito-kotlin`

Existing test files (`GitSyncImplTest`, `PassStoreImplTest`, `DecryptionImplTest`) rewritten in JUnit5 + MockK as part of this effort.

No coverage gate yet — add tests first.

## Production Code Changes Required

- **TODO**: verify `file://` protocol actually works end-to-end before treating it as supported.

---

## Test Cases

### SessionManager — 1 instrumented test

Setup: real `AndroidKeyStore` impl of `SessionKeyStore`; mock `BiometricPrompter` returns pre-initialized cipher.

| #   | Test                                                    | Assertion                     |
| --- | ------------------------------------------------------- | ----------------------------- |
| 1   | `createSession(passphrase)` → `getPassphrase(activity)` | returns original `passphrase` |

---

### EntryBrowserViewModel — 9 unit tests ✓

`EntryBrowserViewModelTest.kt`

| #   | Test                                                          |
| --- | ------------------------------------------------------------- |
| 1   | `init` — `defaultViewTree` pref reflected in `state.treeView` |
| 2   | `init` — `passStore.index` updates `state.entries`            |
| 3   | `setSearchQuery("")` → entries = full index                   |
| 4   | `setSearchQuery("q")` → entries = `passStore.search(q)`       |
| 5   | `toggleView()` → flips `treeView`, clears `collapsedDirs`     |
| 6   | `toggleDir(d)` → first call adds, second removes              |
| 7   | `pull()` → `syncing` true→false, `gitSync.pull()` called      |
| 8   | `pull()` while already syncing → no-op                        |
| 9   | `pull()` exception swallowed → `syncing` false                |

### SettingsViewModel — 7 unit tests ✓

`SettingsViewModelTest.kt`

| #   | Test                                                          |
| --- | ------------------------------------------------------------- |
| 1   | `init` — `syncStatus()` success → `state.lastSyncTime` set    |
| 2   | `init` — `syncStatus()` throws → `state.lastSyncTime` null    |
| 3   | `init` — `gpgKeyInfo` populated from `getGpgKeyInfo()`        |
| 4   | `sessionActive` derives from `sessionOperations.sessionState` |
| 5   | `clearSession()` → `endSession()` called                      |
| 6   | `clearAllData` → `clearing` true→false, `onComplete` invoked  |
| 7   | `clearAllData` → `clearAllKeys()` + `clearAll()` called       |

_Skipped: trivial preference setters (setSessionTimeout, setClipboardTimeout, setDefaultView)._
