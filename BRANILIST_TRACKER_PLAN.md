# Branilist tracker integration plan

The native Branilist tracker is implemented on `feature/branilist-tracker` in the Komikku fork. The scope and validation notes are in [BRANILIST_TRACKER.md](BRANILIST_TRACKER.md).

## Completed in this branch

- Added Branilist search, account/library reads, and library updates.
- Added OAuth authorization code flow with PKCE, local callback forwarding, `state` verification, secure token storage, and refresh-token rotation.
- Added tracker settings, callback routing, icon, and debug app label.
- Added mapper tests, a loopback binding test, and end-to-end validation steps.
- Built debug APK variants; the arm64 APK is `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
- Changed OAuth callback host from `localhost` to the IPv4 loopback literal `127.0.0.1` to avoid device/browser hostname resolution mismatch.
- Moved callback state validation and PKCE token exchange into the loopback listener before the app handoff, so Android no longer needs to deliver the authorization code in a custom-scheme deep link. Callback and token-exchange errors are written to the app log without logging codes or tokens.

## Validation and remaining work

- `spotlessApply`: passed.
- `spotlessCheck`: passed.
- Branilist mapper and callback tests: all 6 passed.
- `assembleDebug`: passed. D8 emits repetitive Kotlin metadata compatibility warnings from dependencies in this environment; dexing and packaging completed.
- The Branilist developer app must now register the exact redirect URI `http://127.0.0.1:3000/oauth/callback` before manual OAuth testing.
- Install the arm64 APK and test OAuth, search, linking, progress/status/score updates, refresh-token rotation, and repeat-count preservation with the registered Branilist account.
- Do not open an upstream pull request until real account validation is complete and the user explicitly approves it.
