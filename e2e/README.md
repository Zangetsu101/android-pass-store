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

| Entry                | Path                            |
| -------------------- | ------------------------------- |
| `admin`              | `example.com/admin`             |
| `testuser`           | `github.com/testuser`           |
| `testuser@gmail.com` | `google.com/testuser@gmail.com` |
| `twitter`            | `social/twitter`                |

Onboarding flow waits until `admin` is visible — that's the readiness signal. Entry tap flow uses `testpass` as the session passphrase.

## Run

```bash
# Full run: build → install → test all flows
./gradlew e2e

# Single flow
./gradlew e2e -Pflow=onboarding
./gradlew e2e -Pflow=onboarding_gpg_auth
./gradlew e2e -Pflow=entry_tap

# Install only (build + install + push key, no test run)
./gradlew e2eInstall
```

## Flows

| File                            | What it tests                                             |
| ------------------------------- | --------------------------------------------------------- |
| `flow_onboarding.yaml`          | Welcome → GPG import → git clone (device key)             |
| `flow_onboarding_gpg_auth.yaml` | Welcome → GPG import (auth subkey) → git clone (gpg auth) |
| `flow_entry_tap.yaml`           | Tap entry → passphrase → biometric prompt                 |
| `flow_all.yaml`                 | `onboarding` + `entry_tap` chained (use for CI)           |

## Environment variables

```bash
export MAESTRO_CLI_NO_ANALYTICS=1
export MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
```
