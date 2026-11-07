# Cloudflare edge configuration

Point the ERP hostname to the Nginx gateway and use **Full (strict)** TLS. Keep the origin private and accept inbound HTTPS only from Cloudflare IP ranges or a Cloudflare Tunnel.

Enable managed WAF rules, exposed-credential checks, bot protection and rate limits for `/auth/*`, `/oauth2/*` and `/api/*`. Never cache `/api/*`, `/auth/*`, `/oauth2/*` or `/login/oauth2/*`; cache fingerprinted frontend assets only.

For an internal ERP, protect the application with Cloudflare Access. The OAuth2 callback paths and Shopify webhook endpoint must use service policies or bypass interactive Access while retaining their application-level state/HMAC validation. Do not expose MySQL, Redis, Keycloak management or Spring Actuator metrics publicly.

Store Cloudflare API tokens in the deployment secret manager. Required permissions should be limited to Zone DNS, WAF/rulesets and Access applications for the ERP zone.
