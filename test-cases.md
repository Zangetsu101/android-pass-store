# Test Cases

Manual test cases to implement as instrumented or UI tests.

---

## Biometric / Auth

- [ ] **PIN/pattern fallback on API 30+** — device with no biometric enrolled but PIN set: `getPassphrase()` shows credential prompt, decrypts passphrase, session remains active
- [ ] **Biometric-only on API 26–29** — device with PIN but no biometric: prompt shown with biometric only; PIN option absent
- [ ] **No credentials at all** — device with no screen lock and no biometric: app blocks unlock with clear error; no crash
