# Production deployment

Meant uses three production services:

- Supabase for PostgreSQL, Auth, and Storage
- Railway for the Spring Boot API
- Vercel for the TanStack Start frontend

The GitHub `main` branch is the production branch. GitHub Actions verifies the repository and then
applies Supabase migrations. Railway and Vercel deploy the same commit through their GitHub
integrations.

## 1. Supabase

Create a production Supabase project in the same geographic region as the Railway backend where
possible. The local Supabase configuration uses PostgreSQL 17, so verify that the production
project uses the same major version.

In the Supabase dashboard:

1. Enable the `vector` PostgreSQL extension.
2. Configure the production Site URL, for example `https://app.usemeant.com`.
3. Add `https://app.usemeant.com/**` and `http://localhost:3000/**` as Auth redirect URLs.
4. Configure the Google and Apple Auth providers that are enabled in the frontend.
5. Configure a custom SMTP provider before enabling production email sign-up and password reset.
6. Copy the project URL, publishable key, project reference, database password, JWKS URL, issuer,
   and Session Pooler connection details.

Do not expose the Supabase `service_role` key in the frontend. The publishable/anon key is intended
for browser use and is protected by Supabase authorization and RLS policies.

### GitHub production secrets

Create a GitHub Environment named `production` and add:

| Secret | Purpose |
| --- | --- |
| `SUPABASE_ACCESS_TOKEN` | Personal access token used by the Supabase CLI |
| `SUPABASE_PROJECT_ID` | Production project reference |
| `SUPABASE_DB_PASSWORD` | Production database password |

After all CI jobs pass on `main`, the `deploy-supabase` job runs `supabase db push`. This applies the
SQL migrations under `supabase/migrations`, including the Storage buckets and their RLS policies.
Application tables are managed separately by Liquibase when the Railway backend starts.

The workflow intentionally does not push all of `supabase/config.toml`, because that file contains
local Auth URLs. Configure production Auth URLs in the Supabase dashboard.

## 2. Railway backend

Create a Railway project from the GitHub repository and configure its service as follows:

| Setting | Value |
| --- | --- |
| Branch | `main` |
| Root Directory | `/backend` |
| Config file path | `/backend/railway.toml` |
| Region | Same region as Supabase where possible |
| Automatic deployments | Enabled |
| Wait for CI | Enabled |

Railway detects `backend/Dockerfile`. The image builds with Java 25 and runs as an unprivileged
user. Railway supplies the `PORT` environment variable; `MEANT_API_PORT` remains a local fallback.
The deployment becomes active only after `/actuator/health` returns HTTP 200.

Use the Supabase Session Pooler on port 5432 for the persistent Spring/Hibernate connection. Do not
use transaction mode on port 6543, because it does not support prepared statements.

### Required Railway variables

```dotenv
SPRING_PROFILES_ACTIVE=production

MEANT_DB_URL=jdbc:postgresql://aws-<region>.pooler.supabase.com:5432/postgres?sslmode=require
MEANT_DB_USER=postgres.<project-ref>
MEANT_DB_PASSWORD=<database-password>
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1

SUPABASE_JWKS_URI=https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_ISSUER_URI=https://<project-ref>.supabase.co/auth/v1

APP_CORS_ALLOWED_ORIGINS=https://app.usemeant.com
MEANT_FORWARD_HEADERS_STRATEGY=native

COMMERCE_AGENT_SITE_URL=https://app.usemeant.com
UCP_AGENT_PROFILE_URL=https://api.usemeant.com/.well-known/ucp-agent.json

MERCHANT_IDENTITY_LINKING_REDIRECT_URI=https://app.usemeant.com/oauth/merchant-callback
MERCHANT_IDENTITY_LINKING_TOKEN_ENCRYPTION_SECRET=<new-strong-random-secret>
```

Add the provider secrets required by enabled features:

```dotenv
OPENROUTER_API_KEY=<secret>
VOYAGE_API_KEY=<secret>

SHOPIFY_AGENT_AUTH_ENABLED=true
SHOPIFY_AGENT_AUTH_ENVIRONMENT=production
SHOPIFY_CLIENT_ID=<secret>
SHOPIFY_CLIENT_SECRET=<secret>

MERCHANT_IDENTITY_LINKING_CLIENT_ID=<secret>
MERCHANT_IDENTITY_LINKING_CLIENT_SECRET=<secret>
ORDER_SHOPIFY_WEBHOOK_SECRET=<secret>
```

If Shopify credentials are not available yet, set `SHOPIFY_AGENT_AUTH_ENABLED=false`; the default
application configuration enables it and validates that credentials are present.

Generate a Railway domain for the first deployment. Add the final API custom domain before setting
`UCP_AGENT_PROFILE_URL` and the frontend API URL.

## 3. Vercel frontend

Import the same GitHub repository into Vercel and configure:

| Setting | Value |
| --- | --- |
| Production Branch | `main` |
| Root Directory | `frontend` |
| Framework Preset | TanStack Start |
| Build Command | Automatic, or `bun run build` |

No `vercel.json` is required. TanStack Start and Nitro are detected by Vercel.

Set these variables for the Production environment:

```dotenv
VITE_MEANT_API_URL=https://api.usemeant.com
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<publishable-or-anon-key>
VITE_EMBEDDED_CHECKOUT_ENABLED=true
VITE_CHECKOUT_KIT_DEBUG=false
```

Every variable prefixed with `VITE_` is included in browser code. Never put a database password,
Supabase `service_role` key, provider credential, or other secret in a `VITE_` variable.

Add the production custom domain, then update the matching Supabase redirect URLs and Railway CORS
origin. Keep preview deployments disconnected from production data until a separate staging
Supabase and backend environment exists.

## 4. Release behavior

On a push to `main`:

1. GitHub Actions runs backend, frontend, and workflow verification.
2. GitHub Actions applies new Supabase SQL migrations.
3. Railway waits for GitHub CI and deploys the backend.
4. Vercel builds and deploys the frontend from `main`.

These providers do not form one atomic deployment. Database migrations and API changes must remain
backward compatible during the rollout. Protect `main`, require the CI checks, and merge through
pull requests instead of pushing unverified changes directly.
