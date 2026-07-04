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
| Exact match               | `"alex\\.dev"`        |
| Substring                 | `".*alex\\.dev.*"`    |
| Case-insensitive          | `"(?i)alex\\.dev"`    |
| Literal dot (e.g. domain) | `".*github\\.com.*"`  |

Avoid asserting on folder labels like `"github.com/"` — the trailing slash and `.` require escaping. Prefer asserting on plain entry names (`"alex\\.dev"`).
