# ADR-012: Own JWT authentication with rotating refresh tokens

**Status:** Proposed

ExoChat issues its own credentials instead of delegating to an external identity provider (`OPERATIONS_SECURITY.md` allows either). The contract is `auth/v1alpha1` (`AuthService`) and `identity/v1alpha1` (`SessionService`).

## Decision

- **Credentials:** email and password. Passwords are hashed with Argon2id (OWASP minimum: 19 MiB memory, 2 iterations, 1 lane; tune upwards), never stored or logged in clear. Policy: 8 to 128 characters, no composition rules, reject passwords known from breaches.
- **Access token:** a JWT signed with ES256, the `kid` header names the key so keys can rotate; verifiers use the published public keys. Lifetime 15 minutes. Claims: `sub` = `users/{user}`, `sid` = session id, `acc` = active account name, `exp`, `iat`. Every protected call authorizes against `sub` and `acc`, the token itself carries no permissions.
- **Refresh token:** opaque random 256-bit value, single use. Only its SHA-256 hash is stored. Every `RefreshSession` returns a new refresh token and spends the old one. A spent token that is presented again means it leaked, so the whole session is revoked. Lifetime 30 days since last use, capped at 90 days since login, after that the user logs in again.
- **Session storage:** sessions and refresh token hashes live in MariaDB (table `session`), the database is the source of truth. Valkey is deferred; it may later hold a revoked-`sid` set for instant logout and rate limit counters, but never the only copy of a session.
- **Session:** one per logged in device, resource `users/{user}/sessions/{session}`, holds the refresh token family, the active account, device label and timestamps. Revoking it (`DeleteSession`) kills the refresh token at once; access tokens already issued live until they expire, which is why they are short. `ChangePassword` (needs the old password) revokes all other sessions, the current one stays. `SwitchAccount` only works on the session of the caller and only for accounts of the same user.
- **Accounts:** a user owns several accounts (personas). The session has one active account, switched with `SwitchAccount`, which issues a new access token with the new `acc` claim.
- **Unauthenticated calls:** only `RegisterUser`, `AuthenticateUser` and `RefreshSession`. Failures on login are `UNAUTHENTICATED` with one message for unknown email and wrong password. Rate limits per email and per address on all three.
- **Transport:** `authorization: Bearer <access_token>` metadata on gRPC, the same on the gateway. A global gRPC interceptor (`AuthInterceptor`) verifies the token without a database lookup and exposes the identity to handlers as `Caller`; only the three unauthenticated calls and the health check are public. Services authorize on `Caller` (a user only reaches its own resources: `PERMISSION_DENIED` for another user's parent or name). WebSocket connections authenticate once with an access token and are closed when it expires unless refreshed.

## Configuration

Properties under `exochat.auth` (`AuthProperties`): `issuer`, `access-token-ttl` (15m), `refresh-token-idle-ttl` (30d), `refresh-token-max-ttl` (90d), `signing-key-id`, `signing-private-key` (PKCS#8 PEM) and `signing-public-key` (X.509 PEM), normally set through `EXOCHAT_AUTH_*` environment variables. Without a private key an ephemeral key is generated at startup and a warning is logged: fine for development, all tokens die with the process.

## Consequences

- No dependency on an external provider and full control of the account model (user with personas), at the price of owning password storage, rate limiting, key management and the security review.
- Short access tokens mean a revoked session is only truly dead after at most 15 minutes. If that is not enough, add a `sid` revocation check backed by Valkey (deferred, see Session storage).
- Refresh rotation needs a transactional read-modify-write per session; two parallel refreshes from one client look like reuse, so clients must serialize refreshes.
- `libs/api` gets `SessionName`. Signing keys need a home (configuration now, a secret store later) and a rotation procedure.

## Open

Email verification, password reset, second factor (TOTP, passkeys), account lockout and device trust are not part of this ADR and need their own decisions. The `TODO` markers in `auth.proto` point at them.
