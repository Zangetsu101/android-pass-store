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

## Acceptance Checklist

```
[auto] buildIndex() parses web/github.com/alice.gpg → domain=github.com, username=alice
[auto] buildIndex() handles 1-level path (github.com.gpg) → domain=null, username=github.com
[auto] buildIndex() ignores non-.gpg files
[auto] resolve(domain) exact match returns that entry first
[auto] resolve(domain) subdomain match: github.com resolves gist.github.com query
[auto] resolve(domain) fuzzy fallback: "githubb.com" still returns github.com entry
[auto] resolve(domain) returns empty list when no candidates
[auto] resolve(packageName) matches com.github.android → github.com entry
[auto] search(query) is case-insensitive
[auto] search(query) ranks closer matches higher
```

## Non-Goals (v1)
- URL field parsing from decrypted file content
- OTP / pass-otp entries
- pass extensions (custom fields)
