# GPG Auth Key Support — Onboarding Rework

Reorder onboarding so GPG import comes **before** clone, and let the user reuse an
ed25519 **Authentication** subkey from the imported GPG key as the SSH key for git
remote access (instead of always generating a device key).

## Flow change

Old: `Welcome → CloneRepo(url + eager ssh-gen) → GpgImport → CloneProgress`
New: `Welcome → GpgImport (1/2) → Clone (2/2, ssh-source toggle) → CloneProgress → Browser`

## Resolved decisions

| #   | Decision                            | Choice                                                     | Rationale                                                                                                                                                             |
| --- | ----------------------------------- | ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Derive model                        | **Extract once, store like device key**                    | Decrypt auth subkey during onboarding, store standalone in `SshKeyStore` (Keystore-wrapped). All future syncs stay passphrase-free, identical to today. See ADR 0001. |
| 2   | Passphrase prompt point             | **On `$ git clone` tap**                                   | Detect free (public cert) on scan; prompt only when committing to clone with `gpg` selected. Also validates passphrase early.                                         |
| 3   | Algorithm support                   | **ed25519 only**                                           | Matches designs + existing store. Non-ed25519 auth subkey → treated as no usable key, silent fallback to device gen.                                                  |
| 4   | Multiple auth subkeys               | **Pick newest automatically**                              | Skip the 03e picker (rare case).                                                                                                                                      |
| 5   | Detection bottom-sheet (OptAPrompt) | **Skip**                                                   | Land directly on clone screen with toggle pre-set to `use gpg auth key`.                                                                                              |
| 6   | Provenance                          | **Persist `ssh_key_source` pref** (`gpg_auth` \| `device`) | Settings display + debuggability.                                                                                                                                     |
| 7   | Copy button on reuse (03)           | **Add it**                                                 | GitHub does NOT register an SSH key from an uploaded GPG key; user often must add the derived `ssh-ed25519` pubkey under GitHub → SSH keys.                           |
| 8   | Passphrase prompt mechanism         | **One-off extraction prompt**                              | Don't warm a decrypt session; keep onboarding/session concerns separate.                                                                                              |

## Feasibility notes / risks

- Extracting ed25519 from `OpenPGPKey`: key-flags = `AUTHENTICATION` + curve ed25519
  readable from public cert (no passphrase). Private side = 32-byte seed after
  `loadAndUnlock`, rebuildable into the same PKCS8 form `SshKeyStoreImpl` already stores.
- **Watch:** GPG ed25519 appears as both EdDSA-legacy (algo 22) and modern Ed25519 (algo 27);
  cv25519 is encryption-only and must be excluded. Verify with a real fixture early.
- Verify-fingerprint values (ssh SHA256 fp) need computing, not just display.

## Implementation steps

**1. `keymanagement` — auth-subkey detection + extraction**

- `GpgKeyStore`:
  - `findAuthSubkey(): AuthSubkeyInfo?` — parse stored armored key (public, no passphrase).
    Newest subkey with Authentication flag (0x20) AND curve ed25519 (algo 22 + 27, exclude
    cv25519). Returns `{ keyId, sshPublicKey, fingerprint, uid, created }`. `sshPublicKey` via
    the existing `openSshPublicKey` wire-format helper.
  - `extractAuthSubkeySeed(passphrase, keyId): ByteArray` — `loadAndUnlock` → locate subkey →
    32-byte Ed25519 seed. Throws `SessionError.WrongPassphrase` on bad pass.
- New `AuthSubkeyInfo` data class in `KeyTypes.kt`.

**2. `SshKeyStore` — accept external key**

- `importEd25519Seed(seed: ByteArray): String` — build PKCS8 priv + X509 pub (same form as
  `generateSshKey`), store both, return openssh pubkey. `getSshKey()` unchanged.

**3. `AppPreferences` — provenance**

- `KEY_SSH_KEY_SOURCE` (string), flow `sshKeySource`, setter.

**4. `GpgImportViewModel` — fold in scan**

- After `importGpgKey` succeeds, call `findAuthSubkey()`, stash in state (`authSubkey`). No
  passphrase. Clone screen re-queries independently (key already persisted).

**5. `CloneRepoViewModel` — rework**

- Inject `GpgKeyStore` + passphrase path alongside `SshKeyStore`.
- Kill eager `generateSshKey()` in `init`; instead `findAuthSubkey()`:
  - subkey present → `source=gpg`, expose its pubkey/keyId/verify fields, no device gen.
  - none → `source=device`, generate device key (as today).
- `setSource(gpg|device)`: device→generate-if-not-yet; gpg→show derived pubkey.
- `onClone()`: `validateRemoteUrl()`, then
  - `source=gpg` → prompt passphrase (one-off) → `extractAuthSubkeySeed` →
    `SshKeyStore.importEd25519Seed` → set `sshPublicKey` pref + `sshKeySource=gpg_auth`.
    Wrong pass → error, stay.
  - `source=device` → key already stored → `sshKeySource=device`.
  - then `onNext(url)`.

**6. One-off passphrase prompt UI**

- Reuse `PassTextField` in a dialog (model on `SessionStartScreen`). Does not start a session.
  Drives `onClone()` gpg branch.

**7. `CloneRepoScreen` — toggle + variants**

- Segmented `ssh auth · source` control (only when a subkey exists; else device-only).
- gpg state (03): "ed25519 · from gpg auth subkey" + `[A]` + derived pubkey + **copy button** +
  verify-fingerprint disclosure (ssh fp / gpg key / uid / created).
- device state (03d/03c): existing device card (copy + regenerate).
- Renumber scaffold: Clone = **step 2**; GpgImportScreen = **step 1**. Align `total` to design
  (2) vs existing (3) — confirm against `CloneProgressScreen`.

**8. `NavGraph` — reorder**

- `GpgImport` (object, no arg, step 1); `CloneRepo` (object, no arg, step 2, collects url);
  keep `OnboardingClone(remoteUrl)` for progress. Remove `OnboardingGpgImport(remoteUrl)`.
- `Welcome.onContinue → GpgImport`; `GpgImport.onNext → CloneRepo`;
  `CloneRepo.onNext(url) → OnboardingClone(url)`.
- Splash `gpgDone → CloneRepo` stays correct.
- `CloneProgressViewModel` unchanged — `getSshKey()` is source-agnostic now.

**9. Tests**

- `GpgKeyStoreImplTest`: detect ed25519 auth subkey; ignore RSA/cv25519; pick newest; extract
  seed; wrong-pass throws. Fixtures: ed25519-auth, rsa-auth, no-auth keys.
- `CloneRepoViewModelTest`: default source by detection; toggle; onClone gpg branch stores
  extracted key + provenance; device branch; wrong-pass stays.
- `GpgImportViewModelTest`: scan populates `authSubkey`.

**10. Verify — existing onboarding e2e keeps passing**

- `e2e/flow_onboarding.yaml` encodes the OLD order; must be reworked to the new order then
  confirmed green:
  - Welcome (`> pass init`) → gpg import first (file → `test-key.asc` → `import` → "Key imported
    successfully.") → clone screen (enter `git@...pass-test-store.git`, clone) → wait `"admin"`.
  - Update button labels to match renamed screens.
- **Fixture dependency:** check whether `test-key.asc` carries an ed25519 auth subkey
  (`gpg --show-keys` / list-packets) up front.
  - no → clone defaults to device path, no passphrase prompt → minimal flow change (preferred).
  - yes → gpg path → passphrase prompt at clone → flow must input passphrase + assert toggle, OR
    toggle to "generate new" / swap to a no-auth-subkey fixture to keep it simple.
- `~/.maestro/bin/maestro check-syntax e2e/flow_onboarding.yaml`, iterate clean.
- `./gradlew e2e -Pflow=onboarding` against emulator → confirm green before done.
- Optional: add a second flow exercising the gpg-reuse branch (passphrase + toggle).

## Build order

1→2→3 (data/key layer, unit-testable in isolation) → 4,5 (VMs) → 6,7 (UI) → 8 (wire nav) →
9 (tests alongside) → 10 (e2e gate).

## Related docs

- `CONTEXT.md` → "SSH Auth Source" section.
- `docs/adr/0001-derive-ssh-key-from-gpg-auth-subkey.md`.
