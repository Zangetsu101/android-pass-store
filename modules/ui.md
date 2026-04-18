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

## Non-Goals (v1)
- Create / edit / delete entries
- Share sheet integration
- Widget
