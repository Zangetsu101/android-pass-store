# UI Module

## Implementation

- **UI:** Jetpack Compose (`androidx.compose.ui`, Material 3).
- **Navigation:** AndroidX Navigation 3.
- **State:** one ViewModel per screen, `StateFlow<UiState>` collected by Compose.
- **DI:** Hilt ViewModels and injected module services.
- **Clipboard clear:** sensitive clipboard entries plus scheduled clear through `ClipboardClearReceiver` using the configured timeout.

## Responsibility

The user-facing app: onboarding, entry browser, entry detail, sync panel, session start, and settings.

## Screens

**Onboarding**

1. Welcome / prerequisite flow.
2. GPG key import.
3. Git remote URL and SSH key setup.
4. Initial clone with progress.
5. Index build and transition to browser.

SSH setup uses the selected **SSH Auth Source**: generated **Device key** or derived **GPG auth subkey** when available.

**Entry Browser**

- Searchable list of indexed pass entries.
- Path-derived names/domains only; entry content is not decrypted for listing.
- Search is fuzzy and case-insensitive.
- Manual sync entry points refresh the index after successful pulls.

**Entry Detail**

- Tap entry → decrypt through app UI path.
- App UI may reuse cached passphrase during an active session.
- Shows first-line secret and notes.
- Copy password/card number to clipboard with configured auto-clear timeout.
- Shows git metadata where available.

**Sync Panel**

- Last sync timestamp / status.
- Manual pull.
- Error display for auth failure, unreachable remote, and non-fast-forward history.

**Settings**

- Autofill / credential provider setup entry points.
- Session timeout configuration.
- Clipboard timeout configuration.
- SSH public key display / re-registration support.
- Manual session clear.
- Delete local store / clear all local app data.

## Non-Goals (v1)

- Create / edit / delete entries
- Share sheet integration
- Widget
