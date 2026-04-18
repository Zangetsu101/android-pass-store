# UI Module

## Implementation
- **UI:** Jetpack Compose (`androidx.compose.ui`, `material3`)
- **Navigation:** Navigation Compose 2.8+ with type-safe Kotlin serialization routes
- **State:** one `ViewModel` per screen, `StateFlow<UiState>` collected via `collectAsStateWithLifecycle()`
- **DI:** `@HiltViewModel` + `hiltViewModel()` at each NavGraph destination
- **Clipboard clear:** `ClipboardManager` + coroutine `delay(45_000)` then `clearPrimaryClip()`

## Responsibility
The user-facing app: onboarding, entry browser, sync status, and settings.

## Screens

**Onboarding (first run)**
1. Git remote URL entry
2. SSH key — generate on-device → display public key → user registers on remote
3. GPG key import — paste armored key or import from file
4. Biometric enrollment prompt
5. Initial clone + index build

**Entry Browser**
- Searchable list of all pass entries (decrypted names, not content)
- Tap entry → biometric prompt → show decrypted password + notes
- Copy to clipboard (auto-clear after 45 sec)
- Directory tree navigation as optional view

**Sync Panel**
- Last sync timestamp
- Manual pull button
- Error display (auth failure, network unreachable)

**Settings**
- Autofill service toggle (deep-link to Android autofill settings)
- Session timeout configuration
- SSH public key display (for re-registration)
- Clear all data / sign out

## Acceptance Checklist

```
Onboarding
[manual] fresh install lands on onboarding (not browser)
[manual] SSH public key displayed in copyable format after generation
[manual] GPG key import accepts pasted armored key
[manual] GPG key import accepts file picked from storage
[manual] progress indicator shown during initial clone
[manual] clone error (bad URL, auth failure) shown with retry option
[manual] completing onboarding lands on entry browser

Entry Browser
[manual] all entries visible after sync, directory structure preserved
[manual] search filters entries in real time as user types
[manual] search is case-insensitive
[manual] tapping entry triggers biometric prompt
[manual] password + notes displayed after biometric success
[manual] copy button copies password to clipboard
[manual] clipboard auto-cleared after 45 seconds

Sync Panel
[manual] last sync timestamp shown and updates after pull
[manual] manual pull button triggers sync and refreshes entry list
[manual] network error shown with last-known-good timestamp
[manual] SSH auth failure shown with link to SSH key screen

Settings
[manual] autofill toggle deep-links to Android autofill system settings
[manual] session timeout change takes effect immediately
[manual] SSH public key viewable for re-registration
[manual] "Clear all data" wipes keys, repo, and DataStore — lands on onboarding
```

## Non-Goals (v1)
- Create / edit / delete entries
- Share sheet integration
- Widget
