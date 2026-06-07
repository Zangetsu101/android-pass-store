# Maestro Test Notes

## API Policy

Maestro YAML API changes between versions. Training knowledge of command names and properties may be stale.

After writing or editing any flow, validate with:

```bash
~/.maestro/bin/maestro check-syntax <file>
```

Fix errors, re-check, iterate. Do not assume a command or property is valid without checking.

## Text Matching

Maestro uses the `text` field as a **full-string regex** per accessibility node — not substring contains.

| Goal                      | Syntax                |
| ------------------------- | --------------------- |
| Exact match               | `"admin"`             |
| Substring                 | `".*admin.*"`         |
| Case-insensitive          | `"(?i)admin"`         |
| Literal dot (e.g. domain) | `".*example\\.com.*"` |

Avoid asserting on folder labels like `"example.com/"` — the trailing slash and `.` require escaping. Prefer asserting on plain entry names (`"admin"`).
