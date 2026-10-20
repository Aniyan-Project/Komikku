# Branilist tracker for Komikku

This feature branch adds Branilist as a native manga tracker to the Komikku fork.

## OAuth app configuration

The Android client uses public OAuth with PKCE S256. It does not contain a client secret. Configure the Branilist app with this exact redirect URI:

```text
http://127.0.0.1:3000/oauth/callback
```

The `127.0.0.1` callback is served by Komikku on the Android device. HTTP is required for this loopback callback; Branilist must have the same URI registered exactly. Update the redirect URI in the Branilist app settings before testing (remove or keep the old `localhost` entry as desired). A server using port 3000 on another machine does not conflict with the Android listener.

The login request asks for `profile:read`, `library:read`, and `library:write`. Grant those scopes to the app in Branilist. The app's public client ID is embedded in the APK; it is not a secret.

Komikku starts a loopback-only listener on port 3000, receives Branilist's registered redirect, validates `state`, and exchanges the authorization code with PKCE before returning to the app. The code and tokens are not placed in the app's private URI scheme. The PKCE verifier is stored in private preferences.

## Supported behavior

- Search Branilist manga, including catalog alternative titles, and add/link a library entry. Search uses Branilist's `searchMedia` discovery query rather than the simpler public REST search, which only matches primary title fields.
- Read the account's library status, progress, score, and repeat count.
- Edit and sync reading start and completion dates, plus private tracking.
- Update status, progress, score, dates, and privacy while preserving the existing repeat count.
- Show English synopsis, release date, and Branilist's average rating in search results.
- New entries start as `PLANNING` with a local score of `0`; adding read progress to a planned entry moves it to `CURRENT`.
- Refresh access tokens using Branilist's rotating refresh-token flow.
- Translate Branilist statuses to Komikku's shared tracker status values.
- Use Branilist's official mark from `media.branilist.com` for the tracker icon.

The tracker can remove a Branilist library entry through the authenticated `removeMediaEntry` GraphQL mutation. The API exposes a maximum of 500 library entries per response; lookup uses that endpoint as provided.

Date and privacy support requires the Branilist API migration `000144_private_library_entries`. Private entries remain available to their owner, but are excluded from public lists, activity feed, public statistics, and social recommendation signals.

## Build and validate

Build the separate debug APK with:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug build installs as `app.komikku.dev`, separate from the production Komikku app. ABI-specific APKs are written to `app/build/outputs/apk/debug/`, including `app-arm64-v8a-debug.apk` for most current Android phones.

Manual end-to-end check:

1. Install the debug APK and open Settings → Tracking → Branilist.
2. Log in and approve the three requested scopes.
3. Search for a Branilist manga, add it to tracking, and verify it appears in the Branilist library.
4. Search for a manga using one of its alternative titles and confirm the result is found.
5. Add an untracked manga; verify it starts as **Planejo ler** with score `0`.
6. Add read progress to a planned manga; verify its status changes to **Acompanhando** on Branilist.
7. Set reading start and completion dates, sync, refresh, and verify both dates persist.
8. Mark a tracked item private; confirm the private icon is enabled after refresh and the item is absent from the public Branilist list/feed. Turn privacy off and confirm public visibility returns.
9. Change progress, status, and score in Komikku; confirm each update on Branilist.
10. Confirm an existing repeat count remains unchanged after an update.
11. Check search results show English synopsis, release date, and average rating where Branilist has them.
12. Log out, log in again, and verify refresh-token rotation continues to work.

If Branilist shows an invalid OAuth request, check the API log for `GET /api/oauth/authorize/preview`. A 400 confirms the request was rejected before consent and before the device callback; current API access logs do not report which individual validation failed.

An upstream pull request is intentionally deferred until the APK has been exercised against a real Branilist account and the integration is confirmed working.
