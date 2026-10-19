# Branilist tracker integration plan

The native Branilist tracker is implemented on `feature/branilist-tracker` in the Komikku fork. The scope and validation notes are in [BRANILIST_TRACKER.md](BRANILIST_TRACKER.md).

## Completed in this branch

- Added Branilist search, account/library reads, and library updates.
- Added OAuth authorization code flow with PKCE, local callback forwarding, `state` verification, secure token storage, and refresh-token rotation.
- Added tracker settings, callback routing, icon, and debug app label.
- Added mapper tests and end-to-end validation steps.
- Built debug APK variants; the arm64 APK is `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

## Validation and remaining work

- `spotlessApply`: passed.
- `spotlessCheck`: passed.
- Branilist mapper unit tests: passed.
- `assembleDebug`: passed. D8 emits repetitive Kotlin metadata compatibility warnings from dependencies in this environment; dexing and packaging completed.
- Full `:app:testDebugUnitTest`: one existing `ScreenSerializationTest` fails with `UnsupportedClassVersionError` under the installed Java 17. All three Branilist mapper tests passed. Retrying with Android Studio's bundled Java 25 did not finish within this run.
- Install the arm64 APK and test OAuth, search, linking, progress/status/score updates, refresh-token rotation, and repeat-count preservation with the registered Branilist account.
- Do not open an upstream pull request until real account validation is complete and the user explicitly approves it.
