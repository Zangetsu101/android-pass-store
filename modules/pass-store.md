# Pass Store Module

## Implementation
- **Index:** In-memory `List<PassEntry>`, rebuilt on each sync via `Dispatchers.Default`
- **Fuzzy match:** Apache Commons Text `LevenshteinDistance` or a token-overlap heuristic — no external ML dependency
- **Scoping:** Hilt `@Singleton` — index shared between UI ViewModel and `AutofillService`
- **Exposed as:** `StateFlow<List<PassEntry>>` so UI and autofill react to index rebuilds after sync

## Responsibility
Index the local git working copy into a searchable entry list. Resolve autofill queries (app package name or web domain) to candidate entries. Parse entry paths into structured metadata.

## Entry Model
Each `.gpg` file in the repo maps to one entry:

```
Path:     web/github.com/alice.gpg
          └── domain: github.com
              username: alice
              encrypted blob: alice.gpg (not decrypted here)
```

```kotlin
data class PassEntry(
    val path: String,           // relative path in repo
    val domain: String?,        // second-to-last path component (if ≥2 levels deep)
    val username: String,       // last path component (filename without .gpg)
    val encryptedFile: File,
)
```

## Matching Strategy
Given a query (domain or app package name):

1. **Exact match** — entry domain equals query domain (e.g. `github.com == github.com`)
2. **Subdomain match** — entry domain is a suffix of query (e.g. `github.com` matches `gist.github.com`)
3. **Fuzzy match** — normalized Levenshtein / token overlap against entry name and domain. Used as fallback, results ranked by score.

Matching is done entirely against the index (filenames/paths) — no decryption at match time.

## Interfaces
- `buildIndex(): List<PassEntry>` — scan local git working copy, parse all `.gpg` paths
- `search(query: String): List<PassEntry>` — ranked list for manual browser search
- `resolve(domain: String): List<PassEntry>` — ranked candidates for autofill
- `resolve(packageName: String): List<PassEntry>` — autofill from app package name

## Non-Goals (v1)
- URL field parsing from decrypted file content
- OTP / pass-otp entries
- pass extensions (custom fields)
