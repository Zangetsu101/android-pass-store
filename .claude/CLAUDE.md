# Android Pass Store

## Android API Policy

Knowledge of Android APIs may be stale. Before making any assumption about Android API behavior, availability, or best practice — use the `android-cli` skill to consult current docs. This applies to: Jetpack libraries, SDK APIs, manifest attributes, permissions, lifecycle, intents, and any API where version matters.

Do not guess. Look it up first.

## Environment

WSL setup. Emulator runs on Windows side via `android emulator` command (not inside WSL). ADB connects to it over TCP. Build happens in WSL; deploy/run targets the Windows-hosted emulator.

## Explanation Style

User knows web/React well, Android ecosystem poorly. When explaining Android concepts, anchor to web/React equivalents. Examples: ViewModel ≈ React state/store, Composable ≈ React component, NavGraph ≈ React Router, Activity ≈ browser tab, Intent ≈ deep link / window.open, Manifest ≈ app config/meta tags, Gradle ≈ package.json + webpack config, CoroutineScope ≈ async context / AbortController scope.
