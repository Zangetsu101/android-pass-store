# pass.android

> Modern Android autofill for your [`pass`](https://www.passwordstore.org/) password store — biometric-gated, read-only, syncs over SSH.

## Motivation

I use [`pass`](https://www.passwordstore.org/), the standard Unix password store, and I wanted a phone client that fit my own workflow: passwords live in a git repo, get written from my laptop, and get _read_ on my phone — mostly through autofill.

Three things pushed me to build this:

- **First-class modern autofill.** I wanted deep integration with the current Android credential stack — `AutofillService`, Credential Manager (Android 14+), and Android 16's Identity Check biometric-only enforcement — not a bolt-on.
- **Different scope from existing clients.** [Android Password Store (APS)](https://github.com/android-password-store/Android-Password-Store) was archived on **Oct 15, 2024**; [an active fork](https://github.com/agrahn/Android-Password-Store) continues it as a full-featured read/write client. Neither targets the modern Android credential stack the way this app does.
- **Built for my flow.** This is a personal project shaped around how I actually use `pass`: read on the phone, write on the laptop.

It's a personal-use project first. But if you're a `pass` user looking for a maintained, autofill-first Android client, it may fit you too.

## Objectives

What the app does:

- **Onboard** — generate an on-device SSH keypair for git auth, import your existing GPG key, and optionally reuse an authentication `[A]` subkey if one is available.
- **Sync** — clone and pull your `pass` repo from a git remote over SSH. Conflict resolution is intentionally out of scope because the app is read-only.
- **Browse & search** — list all entries with directory structure preserved; fuzzy, case-insensitive search.
- **Decrypt & view** — GPG-decrypt an entry behind a biometric prompt, show password and notes.
- **Autofill** — surface credentials to the system via `AutofillService` (Android 8+), inline suggestions (Android 11+) and Credential Manager (Android 14+).
- **Manage** — session timeout, manual lock, manual sync, and clear-all-data, in settings.

## Screenshots

|                                   Welcome                                    |                    |                                 Import GPG key                                  |
| :--------------------------------------------------------------------------: | :----------------: | :-----------------------------------------------------------------------------: |
| <img src="docs/screenshots/01-welcome.png" width="300" alt="Welcome screen"> | &nbsp;&nbsp;&nbsp; | <img src="docs/screenshots/02-gpg-import.png" width="300" alt="GPG key import"> |

|                                     Key loaded                                      |                    |                                    Key imported                                     |
| :---------------------------------------------------------------------------------: | :----------------: | :---------------------------------------------------------------------------------: |
| <img src="docs/screenshots/03-gpg-key-loaded.png" width="300" alt="GPG key loaded"> | &nbsp;&nbsp;&nbsp; | <img src="docs/screenshots/04-key-imported.png" width="300" alt="GPG key imported"> |

|                                       Clone repo                                        |                    |                                   Clone progress                                    |
| :-------------------------------------------------------------------------------------: | :----------------: | :---------------------------------------------------------------------------------: |
| <img src="docs/screenshots/05-git-clone.png" width="300" alt="Clone git repo over SSH"> | &nbsp;&nbsp;&nbsp; | <img src="docs/screenshots/06-clone-progress.png" width="300" alt="Clone progress"> |

|                                         Entry browser                                         |                    |                                        Session start                                        |
| :-------------------------------------------------------------------------------------------: | :----------------: | :-----------------------------------------------------------------------------------------: |
| <img src="docs/screenshots/07-entry-browser.png" width="300" alt="Entry browser with search"> | &nbsp;&nbsp;&nbsp; | <img src="docs/screenshots/09-session-start.png" width="300" alt="Biometric session start"> |

|                                          Entry detail                                          |                    |                                                      Password revealed                                                       |
| :--------------------------------------------------------------------------------------------: | :----------------: | :--------------------------------------------------------------------------------------------------------------------------: |
| <img src="docs/screenshots/10-entry-detail.png" width="300" alt="Entry detail, secret masked"> | &nbsp;&nbsp;&nbsp; | <img src="docs/screenshots/10-entry-detail-revealed.png" width="300" alt="Entry detail, password revealed behind biometric"> |

|                                Settings                                 |
| :---------------------------------------------------------------------: |
| <img src="docs/screenshots/08-settings.png" width="300" alt="Settings"> |

## Goals

Design principles the app is judged against:

- **Security-first** — biometric required per fill, keys wrapped by a hardware-backed Keystore key, decrypted secrets never written to disk, clipboard auto-cleared, timed session lock.
- **Offline-first** — fully functional after the first sync; local git clone, decrypt on demand, no network needed to read.
- **Modern-Android-native** — built on Jetpack Compose, Credential Manager, and Android 16 Identity Check — not a legacy-API port.
- **`pass`-compatible** — standard store layout, GPG, and git remote; no proprietary format, your existing store works as-is.
- **Terminal-themed** — monospace, terminal-inspired UI that mirrors the `pass` CLI aesthetic.

## Running locally

### Prerequisites

- JDK 17+ (CI uses 21)
- [Android CLI](https://developer.android.com/tools/agents/android-cli/download) (`android`)
- A device: a USB-debugging phone, or an emulator (x86_64 emulators need [hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration))

```bash
android sdk install platforms/android-37.0 platform-tools
echo "sdk.dir=$(android info sdk)" > local.properties   # gitignored; Gradle doesn't find the SDK on its own
android emulator create                 # one-time: downloads the emulator + system image, creates AVD "medium_phone"
android emulator start medium_phone     # returns once booted; skip if you're using a phone
android run :app                        # build, install and launch the debug build
```

On Windows, write `sdk.dir` with forward slashes (`C:/Users/...`). Backslashes are escape characters in `.properties` files.

<details>
<summary>Without the Android CLI</summary>

Install `platforms;android-37.0`, `platform-tools`, `emulator` and a system image (e.g. `system-images;android-36;google_apis;x86_64`) with `sdkmanager` or Android Studio, then:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
emulator -avd <your-avd> &      # or plug in a phone
./gradlew installDebug          # build + install on the connected device
```

</details>

### Debug builds use a fixed SSH key

In debug builds, "generate on device" always produces the **same** ed25519 key, derived from a hard-coded seed (`SshKeyStoreImpl.generateTestSshKey`), so e2e runs are deterministic. Consequences:

- The key is public — anyone reading this source can derive its private half. **Never register it on a repo or account you care about.**
- GitHub allows each SSH key to be registered only once, and this one is already the deploy key of [`Zangetsu101/pass-test-store`](https://github.com/Zangetsu101/pass-test-store). Adding it elsewhere fails with _"Key is already in use"_, so **a debug build can only clone `Zangetsu101/pass-test-store.git`**.
- That store is encrypted to the test GPG keys in the same repo (`test-key.asc`, passphrase `testpass`). `./gradlew e2eInstall` pushes them to the device's `Download/` folder for import. See [`e2e/README.md`](e2e/README.md).

To use your own store, install a release build, which generates a random key:

```bash
./gradlew installRelease   # falls back to debug signing when keystore.properties is absent
```

## Future work

- **OTP / `pass-otp` support:** I plan to move my own 2FA secrets into `pass` and add TOTP autofill support.
- **Considering:** write support (create/edit entries), HTTPS git authentication.

## Scope

This is read-only (v1) and intentionally narrow. See [`architecture/architecture.md`](architecture/architecture.md) for the full architecture and out-of-scope list.
