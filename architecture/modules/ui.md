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
- After decryption, the header shows the **Username Line**. Without a Username Line, it shows the file name.
- Each **Note Field** is a row in a `details` card, with its own copy button. The other lines stay in the `notes` block.
- A row copy uses the password clipboard rules. The clip is sensitive, and the clipboard clears after the configured timeout.
- Entry detail marks only the last copied secret as `copied`.
- The values of secret Note Fields (`pin`, `cvv`, `secret`, `answer`, `recovery`, and each key that contains `password`) stay blurred until the user taps them. This display rule is in the `browser` package.
- Copy password/card number to clipboard with configured auto-clear timeout.
- Shows git metadata where available.

**Entry Forms**

- The new-entry form and the edit form share the password card and the customize panel.
- The new-entry form opens with a generated password. The edit form keeps the password until the user regenerates or types.
- The `generator` package makes the password. It is a pure function of the settings and a random source, so JVM tests can reach it.
- The save button shows the `pass` command that does the same change.
- Chips above the notes editor add `username:`, `url:` and `email:` lines. A **Flat Store** path gets no `url:` chip. A chip for a key that the notes have already is disabled.
- The closed notes link and the save button count the notes lines. Blank lines do not count.

**Sync Panel**

- Last sync timestamp / status.
- Manual sync.
- Error display for auth failure, unreachable remote, and conflicts between local and remote changes.

**Settings**

- Autofill / credential provider setup entry points.
- Session timeout configuration.
- Clipboard timeout configuration.
- SSH public key display / re-registration support.
- Manual session clear.
- Delete local store / clear all local app data.

## Non-Goals (v1)

- Delete entries
- Share sheet integration
- Widget
