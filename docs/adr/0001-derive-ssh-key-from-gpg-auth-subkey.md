# Derive SSH key from GPG auth subkey (extract-once)

When the imported GPG key carries an ed25519 subkey flagged for Authentication, onboarding offers to reuse it for git SSH instead of generating a device key. We **extract** that subkey's private material once — at clone time, behind a one-off GPG passphrase prompt — and store it in `SshKeyStore` exactly like a device-generated key (Keystore-wrapped, no passphrase required at sync time). All downstream git sync is therefore source-agnostic.

## Considered Options

- **Extract once, store like the device key** (chosen) — sync stays passphrase-free and identical to today; the only new code is detection + extraction during onboarding.
- **Derive per-sync (keep locked)** — never persist a second copy of the private key; unlock the GPG subkey from the encrypted blob on every git op. Rejected: every sync would then need the GPG passphrase/session, a significant regression to the current zero-friction sync UX.

## Consequences

- A copy of the auth subkey's private material now lives in `SshKeyStore`, protected only by the Android Keystore wrapping key — **outside** the GPG passphrase that otherwise guards it. This is the same at-rest posture as the device key, but it is a second, independently-unlockable copy of key material the user may assume is passphrase-bound.
- Only ed25519 auth subkeys qualify; RSA/ECDSA are ignored. The derived `ssh-ed25519` public key is **not** registered on GitHub merely by uploading the GPG key — the user must add it under GitHub → SSH keys.
- A provenance flag records the source (`gpg_auth` vs `device`) for Settings display and debuggability.
