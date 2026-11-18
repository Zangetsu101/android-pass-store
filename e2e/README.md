# Maestro UI Tests

End-to-end flows for android-pass-store using [Maestro](https://maestro.mobile.dev).

## Install Maestro

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

## Prerequisites

- ADB connected to a running emulator (`adb devices` should show a device)

### Test store

Repo: `Zangetsu101/pass-test-store` — keys are fetched automatically by `e2eInstall`.

GPG passphrase: `testpass`

| File                | Key type                                   | Auth subkey |
| ------------------- | ------------------------------------------ | ----------- |
| `test-key.asc`      | ed25519 master + cv25519 [E]               | No          |
| `test-key-auth.asc` | ed25519 master + cv25519 [E] + ed25519 [A] | Yes         |

### Entries

| Entry          | Path                              |
| -------------- | --------------------------------- |
| `alex.dev`     | `dev/github.com/alex.dev`         |
| `alex`         | `dev/gitlab.com/alex`             |
| `alexm`        | `dev/npmjs.com/alexm`             |
| `alex.morgan`  | `work/slack.com/alex.morgan`      |
| `alex`         | `work/notion.so/alex`             |
| `alex`         | `work/linear.app/alex`            |
| `alex`         | `finance/chase.com/alex`          |
| `alex.morgan`  | `finance/paypal.com/alex.morgan`  |
| `alex`         | `finance/wise.com/alex`           |
| `alex`         | `shopping/amazon.com/alex`        |
| `alexm`        | `shopping/etsy.com/alexm`         |
| `alex`         | `entertainment/netflix.com/alex`  |
| `alex`         | `entertainment/spotify.com/alex`  |
| `alex`         | `travel/airbnb.com/alex`          |
| `alex`         | `travel/uber.com/alex`            |
| `personal`     | `cards/visa/personal`             |
| `travel`       | `cards/mastercard/travel`         |

Onboarding flow waits until `alex.dev` is visible — that's the readiness signal. Entry tap flow uses `testpass` as the session passphrase.

## Run locally or in CI

Use a dedicated test emulator. Each scenario clears the Pass Store app data.
The API 36 Google APIs image with the Pixel 6 profile is the CI baseline.
Install Maestro 2.12.1 and have `adb`, `curl`, and `jq` on PATH.
Maestro is found on PATH, then at `~/.maestro/bin/maestro`; `MAESTRO_BIN`
can override its location. Set `ANDROID_SERIAL` when several devices are connected.

```bash
# Build, install, prepare device, and run every scenario
./gradlew e2e

# Run a scenario independently, including its onboarding prerequisite
./gradlew e2e -Pflow=onboarding
./gradlew e2e -Pflow=onboarding_gpg_auth
./gradlew e2e -Pflow=entry_tap

# Build, install, and push fixture keys only
./gradlew e2eInstall
```

## Structure

- `scripts/install.sh` installs the debug APK and downloads pinned public fixture keys.
- `scripts/prepare-device.sh` checks enrollment for the current Android user. If a
  fingerprint exists, it preserves the device credentials and skips enrollment.
  Otherwise it enrolls a fingerprint on an emulator using test PIN `1234` and
  `shared/` Settings flows. Sensor discovery receives one touch; further input waits
  until the enrollment screen is ready. An existing different credential is preserved:
  enroll manually or use a fresh test emulator. Unrecognized fingerprint diagnostics
  fail explicitly rather than being treated as an empty enrollment.
- `scripts/run.sh` prepares the device and runs the selected scenarios with the same
  reports and diagnostics locally and in CI. Analytics are disabled.
- `shared/setup_store.yaml` resets the app, imports the chosen GPG key, clones the
  test store, and waits for `alex.dev`. Scenarios call it explicitly.
- `scenarios/` contains only independently runnable tests. Helpers are outside this
  directory so Maestro cannot accidentally discover and run them as scenarios.

| Scenario | Coverage |
| --- | --- |
| `onboarding` | GPG import without auth subkey, device SSH key, clone |
| `onboarding_gpg_auth` | GPG import with auth subkey, extraction with passphrase, clone |
| `entry_tap` | Fresh GPG-auth onboarding, entry tap, session passphrase, biometric prompt |

Every scenario starts with fresh onboarding; suite order does not matter. Add future
scenarios to `scenarios/` and call `shared/setup_store.yaml` with `KEY_FILE` and
`USE_GPG_AUTH`. Tests must not rely on another scenario's session or navigation state.

Both SSH paths work in debug builds: the device SSH key uses a fixed seed, and the
GPG auth key comes from the public fixture. Both public SSH keys are registered
on GitHub with read access to the test store. Release device keys are random.
These are public test keys: never grant them access to private stores.

## GitHub Actions

`.github/workflows/e2e.yml` runs on pushes to `main` (including merges) and manual dispatch.
It builds the APK before starting the emulator, then invokes `./gradlew e2e`.
Gradle's task cache avoids rebuilding unchanged output. The job uses KVM,
pinned Maestro 2.12.1, and no release signing secrets.

Local output is in `build/e2e/`. CI uploads that directory as
`maestro-results-<SHA>` for seven days, including the JUnit report and Maestro
failure screenshots, hierarchy, and logs. Test assertions are not retried.

```bash
gh workflow run e2e.yml --ref <branch>
```

Runner credential-preservation checks (no device required):

```bash
python3 e2e/scripts/test_prepare_device.py
```
