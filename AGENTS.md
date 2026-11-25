# Android Pass Store

## Android API Policy

Knowledge of Android APIs may be stale. Before making any assumption about Android API behavior, availability, or best practice — use the `android-cli` skill to consult current docs. This applies to: Jetpack libraries, SDK APIs, manifest attributes, permissions, lifecycle, intents, and any API where version matters.

Do not guess. Look it up first.

## Environment

Read `AGENTS.local.md` if it is present, before you use the emulator or run e2e tests.

## App labels

Use lowercase for all labels in the app.

## Test Infrastructure

End-to-end tests use Maestro. See `e2e/README.md` for setup, flows, and the test repo.

## Explanation Style

User knows web/React well, Android ecosystem poorly. When explaining Android concepts, anchor to web/React equivalents. Examples: ViewModel ≈ React state/store, Composable ≈ React component, NavGraph ≈ React Router, Activity ≈ browser tab, Intent ≈ deep link / window.open, Manifest ≈ app config/meta tags, Gradle ≈ package.json + webpack config, CoroutineScope ≈ async context / AbortController scope.

## Agent skills

### Domain docs

Single-context repo: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Scratch folder

`.scratch/` holds local notes, specs and temporary scripts. Git ignores it, but `rg` searches it.
