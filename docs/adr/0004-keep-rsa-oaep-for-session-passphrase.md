# Keep RSA-OAEP for session passphrase storage

We keep RSA-OAEP for the active session passphrase instead of migrating to biometric AES-GCM. RSA lets session creation encrypt with the public key without a biometric prompt, while passphrase retrieval still requires biometric-gated private-key decrypt; direct biometric AES-GCM would require prompting during session creation too, and envelope encryption would add complexity only to remove a ~190-byte passphrase cap that is acceptable for practical GPG passphrases.
