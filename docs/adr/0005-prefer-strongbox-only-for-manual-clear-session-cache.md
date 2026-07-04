# Prefer StrongBox only for manual-clear session cache

StrongBox gives stronger hardware isolation than the normal Android Keystore TEE path, but Android documents it as slower, more resource-constrained, and unnecessary for most apps. The session passphrase key is regenerated whenever a session starts, so using StrongBox for every session makes unlock/session creation noticeably slower on devices that support it.

Decision: the session passphrase cache uses the normal biometric-gated Android Keystore RSA-OAEP key for timed sessions. When the user chooses `sessionTimeoutMinutes == 0` (“until manually cleared”), the session blob may live much longer, so session creation prefers StrongBox if the device exposes `FEATURE_STRONGBOX_KEYSTORE`. StrongBox remains best-effort only: if the device lacks StrongBox or the requested RSA-OAEP key parameters are unsupported, the app falls back to the normal Keystore/TEE path.

Consequences: normal unlock stays fast, manual-clear sessions get the strongest available at-rest protection for the cached passphrase, and future changes must not make StrongBox mandatory for session creation.
