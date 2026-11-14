# Google OAuth Local Setup

Google Login uses the OAuth 2.0 authorization-code flow. Client credentials are
environment-specific and must never be committed to the repository.

## 1. Create a Google OAuth client

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select an
   existing development project or create one for SwiftDrop.
2. Configure the OAuth consent screen:
   - Choose **External** unless the team uses a Google Workspace project.
   - Add the application name and support email.
   - Request the `openid`, `email`, and `profile` scopes.
   - While the app is in **Testing**, add every team member's Google account as
     a test user.
3. Go to **APIs & Services → Credentials → Create credentials → OAuth client ID**.
4. Choose **Web application**.
5. Add these authorized JavaScript origins:
   - `http://localhost:4200` for the Angular development server.
   - `http://localhost:8080` when the full Docker gateway serves the frontend.
6. Add these authorized redirect URIs:
   - `http://localhost:8080/api/v1/auth/oauth2/callback/google` for the full
     Docker Compose profile.
   - `http://localhost:8081/api/v1/auth/oauth2/callback/google` for `ng serve`
     with auth-service running directly on port 8081.

Copy the generated Client ID and Client Secret. The Client ID is not a
credential by itself, but both values must remain in local environment
configuration rather than source control.

## 2. Configure a local checkout

```bash
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env` and set these variables:

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth2/callback/google
GOOGLE_FRONTEND_CALLBACK_URL=http://localhost:4200/auth/callback
```

For the Angular development-server workflow (`ng serve` plus `mvn
spring-boot:run`), use this redirect URI instead:

```dotenv
GOOGLE_REDIRECT_URI=http://localhost:8081/api/v1/auth/oauth2/callback/google
```

The value must be identical to one of the redirect URIs registered in Google
Cloud Console. `deploy/.env` is ignored by Git; do not paste its values into a
commit, issue, pull request, or chat message.

## 3. Start or restart SwiftDrop

### Full Docker Compose workflow

```bash
docker compose --env-file deploy/.env --profile full up --build -d
```

Open `http://localhost:8080/login`.

### Angular development-server workflow

Start infrastructure, auth-service, and Angular separately:

```bash
docker compose --env-file deploy/.env --profile infra up -d
GOOGLE_REDIRECT_URI=http://localhost:8081/api/v1/auth/oauth2/callback/google \
  mvn -B spring-boot:run -pl services/auth-service -Dspring-boot.run.profiles=local
cd frontend
npm ci
npx ng serve web --proxy-config proxy.conf.json
```

Open `http://localhost:4200/login`. Restart the auth-service after changing
`deploy/.env`; refreshing only the browser does not reload Spring properties.

## 4. Test Google Login

1. Open the appropriate login URL.
2. Click **Continue with Google**.
3. Confirm the browser reaches the Google consent/sign-in page.
4. Complete sign-in with a configured test user.
5. Confirm the browser returns to `/auth/callback` and then the customer portal.
6. Confirm no access or refresh token appears in the URL or browser storage.

## Troubleshooting

### `401: invalid_client` / “The OAuth client was not found”

- Inspect the authorization URL without printing secrets:
  `curl -sS -D - -o /dev/null http://localhost:8081/api/v1/auth/oauth2/google`.
- The `client_id` must be the real Client ID from the same Google Cloud
  project, not a placeholder.
- Restart the auth-service after changing environment variables.
- In the Docker workflow, recreate the auth container:
  `docker compose --env-file deploy/.env --profile full up --build -d auth-service`.

### `redirect_uri_mismatch`

The URI in `GOOGLE_REDIRECT_URI` must match Google Cloud Console exactly,
including scheme, port, path, and trailing slash behavior. Register the
`8080` URI for the gateway workflow or the `8081` URI for direct auth-service
development.

### Testing-mode access denied

Add the Google account to the OAuth consent screen's **Test users** list, then
retry. Testing-mode authorizations may expire and require consent again.

### Callback or session handoff fails

Confirm that the selected redirect URI reaches the running auth-service, that
the frontend callback URL is `http://localhost:4200/auth/callback`, and that
the browser is using the same host (`localhost`) throughout the flow.

## Team usage

The recommended approach is one shared development OAuth Web client with its
Client Secret distributed privately to team members. Each member may instead
create a personal development client and register the same local origins and
redirect URIs. In both cases, every member must configure their own ignored
`deploy/.env`; no machine-specific path or username is required.
