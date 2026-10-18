# Branilist tracker integration

## Current architecture

Komikku trackers implement `Tracker` and normally extend `BaseTracker`. `TrackerManager` owns each instance, settings expose tracker login controls, and `TrackLoginActivity` handles OAuth browser callbacks. A tracker maps the shared `Track` model to a service's media, library status, progress, score, search, and refresh operations.

## Branilist API contract confirmed in the Branilist repository

- Public endpoints: `GET /api/v1/search?q=...` and `GET /api/v1/media/:id`.
- User endpoints: `GET /api/v1/me`, `GET /api/v1/me/library`, and `PUT /api/v1/me/library/:mediaId`.
- OAuth: authorization code with PKCE S256, token endpoint `POST /oauth/token`, rotating refresh tokens, and no client secret in a public Android app.
- Documented scopes include `profile:read`, `library:read`, and `library:write`.
- Base API host in the Branilist TypeScript SDK: `https://api.branilist.com`.

## Planned implementation

1. Register a Branilist public OAuth client for the fork and obtain its `client_id` and exact redirect URI. Keep the PKCE verifier and rotating refresh token in private app preferences.
2. Add a Branilist `Tracker` implementation and API/DTO layer for public media search, current user, library lookup, and library updates. Map Branilist statuses and score/progress semantics explicitly.
3. Add the Branilist service to `TrackerManager`, settings login/logout, OAuth callback routing, and tracker status presentation.
4. Give the fork a distinct application ID and display name, disable Komikku's upstream update checker and telemetry destinations, and make a separate test APK.
5. Add API/mapper tests, document account setup and end-to-end validation, then build and test the test variant before considering any upstream contribution.

## Current blocker

Implementation of a real OAuth login cannot be completed or validated until the Branilist OAuth application is registered and its public `client_id` and redirect URI are known. Never put a client secret in this Android app. No upstream pull request will be opened before the integration is tested and explicitly approved.
