# Handoff: pass.android — Password Store App

## Overview

A native Android password manager that acts as a front-end for [pass](https://www.passwordstore.org/) — the standard Unix password manager. Passwords are GPG-encrypted files stored in a git repository. The app handles cloning the repo, importing the GPG keypair, decrypting entries, and manual git sync. OS handles autofill. Biometric unlock is supported as a session re-auth mechanism.

## About the Design Files

The files bundled in this package are **design references created in HTML** — high-fidelity prototypes showing intended look and visual behavior. They are **not production code**. The task is to **recreate these designs in Jetpack Compose**, using its established patterns and the token values defined in this document.

Open `Screens.html` alongside `design-canvas.jsx` and `tweaks-panel.jsx` in the same directory (served locally). Use the **Tweaks** toggle to switch between Dark and Light (Paper Terminal) themes.

## Fidelity

**High-fidelity.** The mocks define exact colors, typography, spacing, and component shapes. Recreate the UI as closely as possible in Compose, adapting only where Android platform conventions require it (system back gesture, ripple touch feedback, etc.).

---

## Release Scope

### v1.0
- Onboarding (clone repo, import GPG keypair)
- Home: tree view + flat list, switchable
- Entry detail: view password (copy/reveal), metadata, notes — **read only**
- Settings: git, GPG, display, store
- Git sync: manual
- Session management + biometric unlock

### v2.0 (future)
- New entry creation
- Password generator
- Edit / delete entries

---

## Design Tokens

### Colors

Two themes are supported. Toggle via the Tweaks panel in `Screens.html`.

#### Dark Theme (default)

```kotlin
object PassColorsDark {
    val Background  = Color(0xFF0B0D0B)
    val Surface     = Color(0xFF0F120F)
    val Raised      = Color(0xFF131A13)
    val Border      = Color(0xFF1D2B1D)
    val Border2     = Color(0xFF243324)
    val Accent      = Color(0xFF39FF6B)  // neon green — add subtle glow
    val AccentDim   = Color(0x2039FF6B)
    val AccentMid   = Color(0x4439FF6B)
    val TextPrimary = Color(0xFFC8E6C9)
    val TextDim     = Color(0xFF527A52)
    val TextFaint   = Color(0xFF2E4A2E)
    val Danger      = Color(0xFFFF5555)
    val Warning     = Color(0xFFFFCC44)
}
```

#### Light Theme — Paper Terminal

```kotlin
object PassColorsLight {
    val Background  = Color(0xFFF5F2EB)  // warm off-white
    val Surface     = Color(0xFFEDE9E0)
    val Raised      = Color(0xFFE8E4DB)
    val Border      = Color(0xFFD4CFC4)
    val Border2     = Color(0xFFC8C2B5)
    val Accent      = Color(0xFF1A6B3C)  // forest green — no glow
    val AccentDim   = Color(0x181A6B3C)
    val AccentMid   = Color(0x351A6B3C)
    val TextPrimary = Color(0xFF1C1A16)
    val TextDim     = Color(0xFF7A7265)
    val TextFaint   = Color(0xFFB0A898)
    val Danger      = Color(0xFFC0392B)
    val Warning     = Color(0xFFB7791F)
}
```

**Phone bezel:** use dark (#1A1A1A) for both themes — better contrast against the light background.

**Glow:** Dark theme only. Apply `BlurMaskFilter` at ~8dp spread, same accent colour, 25% opacity on: FAB, active selection, result card, splash icon.

### Typography

All UI text uses **JetBrains Mono** (monospace). Add to `res/font/` or via Google Fonts Compose dependency.

```kotlin
val JetBrainsMono = FontFamily(/* load from res/font */)

object PassType {
    val Display = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.02).em)
    val Title   = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val Body    = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val Label   = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.12.em)
    val Caption = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 9.sp)
}
```

### Spacing

```
4dp  — tight gap (icon ↔ text, tag padding)
6dp  — small gap (chips, sub-elements)
8dp  — default gap (list items, section padding)
10dp — input vertical padding
12dp — input horizontal padding, card padding
14dp — larger card padding
16dp — screen horizontal padding
18dp — screen content padding
20dp — onboarding content padding
```

### Shape

```kotlin
val PassShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),  // tags, chips
    small      = RoundedCornerShape(4.dp),  // buttons, inputs, cards — PRIMARY
    medium     = RoundedCornerShape(8.dp),  // avatar initials, icons
)
```

---

## Screens

### Onboarding

#### 00 · Splash
Shown on cold launch while the app initialises. Full-screen dark bg, centered lock icon with glow, `pass.android` wordmark, animated loading bar at bottom, scanline texture overlay.

#### 01 · Welcome
App intro with feature tag pills (`gpg`, `git`, `pass`). Single CTA: `$ clone a store`. No "init new store" in v1.

#### 02 · Clone Repo
- Step indicator: **1 / 2**
- Git URL input with `git@` prefix and blinking cursor
- Generated SSH ed25519 public key displayed. Copy + regenerate buttons.
- Warning banner: `⚠ github → Settings → SSH and GPG Keys → New SSH Key`
- CTA: `$ git clone`

**02b · Clone Progress** — terminal-style streaming git output, progress bar with object count, cancel button.

#### 03 · GPG Key
- Step indicator: **2 / 2**
- Two import options:
  1. **Import from file** — `.asc / .gpg secret key file`
  2. **Paste armored secret key** — multiline input prefilled with `-----BEGIN PGP PRIVATE KEY BLOCK-----`
- Export hint: `gpg --armor --export-secret-keys your@email.com`
- CTA: `finish setup →`

---

### Main App — v1.0

#### 04 · Home — Tree View
Top bar: `~/.password-store` path + `pass.android` title. Right: sync chip + grid toggle + gear icon (→ Settings).
Search bar below top bar: `$ grep -r ""` prompt with blinking cursor.
Folder tree with `▼ folder/` rows and `├─ / └─` entry rows. Active entry: `AccentDim` bg.

**04b · Home — Syncing** — sync chip spins, status banner shows `git pull --rebase origin main`, tree dimmed to 45%.

#### 05 · Home — Flat List
Same top bar but toggle shows lines icon. Same search bar. Alphabetical list: index letter + entry name + path + time ago.

#### 05b · GPG Passphrase Prompt
Shown when session has expired (first launch or timeout). Lock icon, "session expired" title, passphrase input, "unlock session" CTA, "session lasts X min" note.

#### 05c · Biometric Unlock
Bottom sheet slides up over dimmed home tree. Fingerprint icon with glow. "unlock session" button. "use passphrase instead" escape link.

**Flow:** Tap entry → check session active → if expired: show 05b (first time) or 05c (biometric) → 06a → 06b.

#### 06a · Entry — Decrypting
Metadata (path, modified, git commit) fully visible immediately — comes from filesystem, not encrypted file.
Password field: spinner + "decrypting…" label. Notes: shimmer skeleton. Copy/reveal buttons shimmer.

#### 06b · Entry Detail
Password: blurred by default (BlurMaskFilter 5dp). Copy button → clipboard, auto-clears in 45s, snackbar confirms. Reveal button → unblur, re-blurs after 45s.
Metadata table always visible. Freeform notes section. No edit/delete in v1.

#### 07 · Settings
Sections and rows:

| Section | Row | Type |
|---|---|---|
| git | remote url | read-only (no chevron) |
| git | ssh key | read-only |
| git | pull on open | toggle (default: off) |
| gpg | key id | read-only |
| gpg | uid | read-only |
| display | default view | tappable → bottom sheet (tree / flat) |
| display | clipboard timeout | tappable → bottom sheet (15s/30s/45s/60s/never) |
| display | session timeout | tappable → bottom sheet (5min/15min/30min/1hr/until app close) |
| display | biometric unlock | toggle (default: off) |
| store | delete local store | destructive — confirm dialog |

**07b–07e** show the toggle and bottom sheet states — see artboards.

Row spec: Surface bg grouped card, Border2 border, radius 4dp. Rows: padding 11×14dp, Border divider. Read-only rows: no chevron. Toggles: 32×18dp pill, Accent when on, Border2 when off, dark bg thumb.

---

### v2.0 — Entry Creation (future)

Home screens with FAB (`+` button, 44×44dp, AccentDim/Accent, radius 4dp).
Entry detail with edit + delete buttons.
New Entry screen: path builder with folder chips, password input, "generate instead" link.
Password Generator: length slider (8–64), charset toggles (A–Z / a–z / 0–9 / !@#…), entropy bar, result card.

---

## Session Management

**Concept:** The GPG private key passphrase is requested once per session to unlock the key in memory. Subsequent decryptions within the session use the in-memory key without prompting again.

**Session lifecycle:**
1. Cold launch or session expired → show passphrase prompt (05b)
2. If biometric unlock is enabled and it's not the first launch → show biometric bottom sheet (05c) instead
3. Passphrase or biometric confirmed → session starts, key held in memory
4. Session expires after configured timeout (default: 30 min)
5. On entry tap within active session → go straight to 06a decrypting

**Key storage:** Never persist the decrypted private key or passphrase. Hold only in process memory. Zero on session expiry or app background.

---

## Interactions & Behavior

### Navigation
- Jetpack Navigation Compose, single `NavHost`
- Onboarding (00–03) on a separate back stack; on finish, pop all and push Home
- Back on 04/05 exits app
- Generator (v2) pushed from New Entry — returns result via `SavedStateHandle`

### Transitions
- Screen transitions: `fadeIn + slideInHorizontally` (300ms, FastOutSlowIn)
- Tree expand/collapse: `AnimatedVisibility`, 200ms height
- Password blur toggle: `BlurMaskFilter` animated 300ms
- Blinking cursor: `InfiniteTransition`, 500ms step easing
- Biometric bottom sheet: `BottomSheetScaffold` or `ModalBottomSheet`

### Clipboard
- `ClipboardManager.setPrimaryClip()` on copy
- `Handler.postDelayed` to clear after configured timeout
- `Snackbar` confirms copy with countdown

### Git Sync
- Manual: sync chip tap → `WorkManager` one-time task: `git pull --rebase` then `git push`
- Pull on open: run pull on app foreground if toggle enabled
- Sync chip spins while running; errors surface as persistent `Snackbar`

### GPG Decryption
- Bundle a native GPG library (e.g. BouncyCastle for PGP, or compile GnuPG as a native lib)
- **Do not use OpenKeychain** — key management is handled in-app
- Import secret key via file picker or armored text paste on screen 03
- Store encrypted private key blob in Android Keystore; decrypt with user passphrase to get in-memory key
- Zero decrypted key buffer on session expiry

### SSH Key Generation
- `java.security.KeyPairGenerator` with Ed25519
- Store private key in Android Keystore (hardware-backed if available)
- Serialize public key in OpenSSH wire format for display/copy

---

## State Management

```
AppViewModel        — session state, sync status, store root path, theme
OnboardingViewModel — step, git URL, SSH keypair, GPG key import state
HomeViewModel       — tree/flat toggle, expanded folders, entry list, search query
EntryViewModel      — selected entry, decrypted password, reveal state, clipboard countdown
SettingsViewModel   — all settings, persisted via DataStore<Preferences>
```

### Persistence
- Store path, git remote, GPG key ID → `DataStore<Preferences>`
- SSH private key → Android Keystore
- Encrypted GPG private key blob → internal app files directory
- Session timeout, biometric toggle, theme, clipboard timeout → `DataStore<Preferences>`
- **Passwords never persisted in plaintext**

---

## Assets

| Asset | Notes |
|---|---|
| JetBrains Mono | [fonts.google.com](https://fonts.google.com/specimen/JetBrains+Mono) — add to `res/font/` |
| Lock / fingerprint icons | Draw as `Canvas` paths — see SVG source in `Screens.html` |
| All other icons | Inline `Canvas`/`Path` in Compose or `Icons.Outlined.*` tinted to theme — no icon libraries |

---

## Files in this package

| File | Purpose |
|---|---|
| `README.md` | This document |
| `Screens.html` | Hi-fi reference for all screens. Open with `design-canvas.jsx` + `tweaks-panel.jsx`. Use Tweaks to toggle dark/light theme. |
| `Light Theme.html` | Light theme explorations (Paper Terminal vs Phosphor Light) |
| `Theme Explorations.html` | Early 3-direction theme exploration |
| `design-canvas.jsx` | Canvas component — required to render HTML files |
| `tweaks-panel.jsx` | Tweaks panel component — required to render `Screens.html` |

---

## Developer Notes

- **Terminal aesthetic is intentional** — no Material3 card elevation, no coloured top bars, no rounded-corner accent borders. Flat, border-driven, monospaced.
- **Dark theme glow:** neon green (`#39FF6B`) should glow subtly on key elements. No glow in Paper Terminal light theme.
- **Override MaterialTheme entirely** — don't let default ColorScheme bleed through.
- **GPG is the hardest part.** BouncyCastle (`bcpg-jdk18on`) can parse and use OpenPGP secret keys in pure Java. Test with real pass stores early.
- **Session zeroing:** use `Arrays.fill(byteArray, 0)` immediately after session expiry; don't rely on GC.
- **Toggle component:** do not use Material3 `Switch` — build a custom 32×18dp pill toggle matching the terminal style (see spec above).
