# Branilist tracker for Komikku

This feature branch adds Branilist as a native manga tracker to the Komikku fork.

## OAuth app configuration

The Android client uses public OAuth with PKCE S256. It does not contain a client secret. Configure the Branilist app with this exact redirect URI:

```text
http://localhost:3000/oauth/callback
```

The `localhost` callback is served by Komikku on the Android device. HTTP is required for this loopback callback; Branilist must have the same URI registered exactly. A server using port 3000 on another machine does not conflict with the Android listener.

The login request asks for `profile:read`, `library:read`, and `library:write`. Grant those scopes to the app in Branilist. The app's public client ID is embedded in the APK; it is not a secret.

Komikku starts a loopback-only listener on port 3000, receives Branilist's registered redirect, then forwards the authorization response to the app's private URI scheme. The PKCE verifier is stored in private preferences and the returned `state` is checked before exchanging the authorization code.

## Supported behavior

- Search Branilist manga and add/link a library entry.
- Read the account's library status, progress, score, and repeat count.
- Update status, progress, and score while preserving the existing repeat count.
- Refresh access tokens using Branilist's rotating refresh-token flow.
- Translate Branilist statuses to Komikku's shared tracker status values.

Branilist's current public API has no library-entry delete endpoint, so unlinking/removing a remote entry is not offered by this tracker. The API exposes a maximum of 500 library entries per response; lookup uses that endpoint as provided.

## Build and validate

Build the separate debug APK with:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug build installs as `app.komikku.dev` and displays as **Komikku Branilist (test)**, separate from the production Komikku app. ABI-specific APKs are written to `app/build/outputs/apk/debug/`, including `app-arm64-v8a-debug.apk` for most current Android phones.

Manual end-to-end check:

1. Install the debug APK and open Settings → Tracking → Branilist.
2. Log in and approve the three requested scopes.
3. Search for a Branilist manga, add it to tracking, and verify it appears in the Branilist library.
4. Change progress, status, and score in Komikku; confirm each update on Branilist.
5. Confirm an existing repeat count remains unchanged after an update.
6. Refresh the tracker entry and verify the values return from Branilist.
7. Log out, log in again, and verify refresh-token rotation continues to work.

If Branilist shows an invalid OAuth request, check the API log for `GET /api/oauth/authorize/preview`. A 400 confirms the request was rejected before consent and before the device callback; current API access logs do not report which individual validation failed.

An upstream pull request is intentionally deferred until the APK has been exercised against a real Branilist account and the integration is confirmed working.
