# Security deployment baseline

## Implemented in the application

- Opaque 12-hour sessions are stored in Redis. Password change and account disable revoke effective access.
- Session-bound double-submit CSRF protection is required for every cookie-authenticated mutation.
- Authentication and MFA have stricter rate limits; all API traffic has Redis-backed read/write limits.
- Authorization is enforced by NestJS guards against company, store and warehouse scopes. Frontend menu filtering is presentation only.
- New passwords use BCrypt cost 12. Existing scrypt hashes remain accepted and are upgraded after successful login.
- Login success/failure, MFA enrollment, logout, permission and data changes are written to the audit log.
- Helmet and Next.js headers enable CSP, HSTS in production, clickjacking, MIME and referrer protection.
- Validation runs at both boundaries: Zod/React Hook Form in the browser and class-validator in the API.
- Client errors hide server details and include a request ID where available. HTTP logs contain method/path/status only, never bodies, cookies, tokens or secrets.

## Required production secrets

Set these in Vercel/hosting secrets, never in Git:

- `CSRF_SECRET`: independent 32-byte random value encoded as 64 hex characters.
- `MFA_ENCRYPTION_KEY`, `INTEGRATION_ENCRYPTION_KEY`, `SHOPIFY_TOKEN_ENCRYPTION_KEY`: different 32-byte random keys.
- `DATABASE_URL`, `REDIS_URL`, Shopify credentials and any external connector credentials.
- `SESSION_COOKIE_SECURE=true` and the exact HTTPS `APP_ORIGIN`.

Rotate one secret at a time with a planned session/token invalidation window. Do not copy production secrets into CI.

## Edge and network controls

The application is currently hosted on Vercel, so Nginx/Caddy is not in the request path. Configure the production domain in Cloudflare with managed WAF rules, bot protection, rate limiting and TLS Full (strict). Use Cloudflare Access or Tailscale for an internal-only admin deployment. Keep the Shopify OAuth callback and webhook routes outside interactive Zero Trust login while retaining their OAuth/HMAC verification.

If self-hosting, place Caddy/Nginx in front of web/API, terminate TLS there, allow only the proxy to reach application ports, and keep PostgreSQL/Redis on a private network. Database credentials should have schema-specific least privilege. Object storage must use private buckets and short-lived signed downloads.

## Optional integrations not activated without external services

- OIDC/Keycloak requires issuer, client ID/secret, redirect URIs and an account migration plan. It should replace—not run beside—the current password/session authority.
- Sentry requires a project DSN. Disable default PII, scrub authorization/cookie/request body fields and use a low trace sample rate before enabling it.
- ClamAV is required when a production file-upload feature is enabled. Stream uploads to quarantine, scan before persistence, enforce size/type limits, and release only clean files.
- OpenTelemetry/Prometheus/Grafana/Loki require an observability endpoint/collector and retention policy. `/api/v1/health/live` and `/api/v1/health/ready` are available for orchestration now.
- Backups require the database/object-storage provider. Enable encrypted point-in-time recovery and perform periodic restore tests.

## Automated checks

- `ci.yml`: lint, typecheck, unit/component tests, build and production dependency audit.
- `codeql.yml`: JavaScript/TypeScript static analysis.
- `security-scan.yml`: Semgrep and Trivy filesystem/configuration scanning.
- Dependabot: grouped npm and GitHub Actions updates.
- `zap.yml`: manual baseline scan against an explicitly supplied **staging** HTTPS URL only.

Protect `main`, require these checks, require review for workflow/permission changes, and use short-lived deployment credentials.
