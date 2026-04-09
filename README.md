# Rels DG OAuth Auth

This project now includes a DG OAuth-based authentication flow.

## Endpoints

- `GET /api/auth/dg/start?redirectUri=...`
  - Starts DG OAuth in browser and redirects to DG authorize page.
- `GET /api/auth/dg/callback?code=...&state=...`
  - Handles DG callback, exchanges token, and returns app JWT payload.
- `POST /api/auth/signin`
  - Exchanges a DG auth code for a DG access token, loads the DG user profile, creates or updates the local user, and returns a JWT.
- `GET /api/auth/me`
  - Returns the current authenticated user from the JWT bearer token.

## Environment variables

- `DATAGSM_CLIENT_ID`
- `DATAGSM_CLIENT_SECRET`
- `DATAGSM_REDIRECT_URIS` (comma-separated allowlist; must include callback URL)
- `DB_HOST` (e.g. `jdbc:mysql://localhost:3306/rels?serverTimezone=Asia/Seoul`)
- `DB_DRIVER` (e.g. `com.mysql.cj.jdbc.Driver`)
- `DB_USER`
- `DB_PASSWORD`
- `DB_DDL` (`update`, `create`, etc.)
- `JWT_SECRET`
- `JWT_EXPIRATION` (minutes)

## Quick start

```powershell
$env:DATAGSM_CLIENT_ID="..."
$env:DATAGSM_CLIENT_SECRET="..."
$env:DATAGSM_REDIRECT_URIS="http://localhost:8080/api/auth/dg/callback,http://localhost:3000/callback"
$env:DB_HOST="jdbc:mysql://localhost:3306/rels?serverTimezone=Asia/Seoul"
$env:DB_DRIVER="com.mysql.cj.jdbc.Driver"
$env:DB_USER="root"
$env:DB_PASSWORD="..."
$env:DB_DDL="update"
$env:JWT_SECRET="..."
$env:JWT_EXPIRATION="60"
.\gradlew test
.\gradlew bootRun
```

## Browser login test

Open this URL in your browser (adjust host/port as needed):

`http://localhost:8080/api/auth/dg/start?redirectUri=http://localhost:8080/api/auth/dg/callback`

## Request example

```json
{
  "authCode": "...",
  "redirectUri": "http://localhost:3000/callback",
  "codeVerifier": "..."
}
```

Send the returned JWT as:

```http
Authorization: Bearer <token>
```

