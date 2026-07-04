# Pass Store Module

## Implementation

- **Index:** in-memory `List<PassEntry>` exposed as `StateFlow<List<PassEntry>>`.
- **Fuzzy search:** Apache Commons Text `LevenshteinDistance` for explicit user search only.
- **Scoping:** Hilt `@Singleton`, shared by UI, AutofillService, and Credential Manager provider.

## Responsibility

Index the local git working copy into structured pass entries. Resolve conservative autofill candidates from indexed paths without decrypting files. Provide fuzzy search for explicit user browsing/search.

Path conventions and entry semantics are defined in [`../../CONTEXT.md`](../../CONTEXT.md).

## Entry Model

```kotlin
data class PassEntry(
    val path: String,        // relative path in repo, including .gpg
    val domain: String?,     // path-derived domain/display parent
    val username: String,    // filename without .gpg
    val encryptedFile: File,
    val isCard: Boolean = false,
)
```

## Matching Strategy

### Automatic web login matching

Given a web domain:

1. **Exact match** — entry domain equals query domain.
2. **Subdomain match** — query is equal to or a subdomain of entry domain.
3. Otherwise return no automatic candidates.

### Automatic native app matching

Given a package name:

1. Reverse package components (`com.github.android` → `android.github.com`).
2. Match entries whose domain appears in the reversed package using dot-bounded checks.
3. Sort longer domains first.

### Explicit user search

`search(query)` performs fuzzy, case-insensitive ranking against username, domain, and path. This is used by the app browser and explicit autofill search, not by automatic external suggestions.

## Card Entries

Card entries are detected at index time by top-level path prefix (`cards/` or `credit-cards/`) and marked with `isCard = true`. Card forms do not use domain matching; all indexed card entries are candidate card fills.

## Interfaces

- `buildIndex(): List<PassEntry>` — scan local git working copy and parse all `.gpg` paths.
- `search(query: String): List<PassEntry>` — fuzzy ranked list for explicit search.
- `resolve(domain: String): List<PassEntry>` — conservative web-domain candidates for autofill.
- `resolveByPackage(packageName: String): List<PassEntry>` — conservative native-app candidates for autofill.

## Non-Goals (v1)

- URL field parsing from decrypted file content
- OTP / pass-otp entries
- pass extensions beyond the currently supported Card Entry fields
- Fuzzy automatic autofill suggestions
