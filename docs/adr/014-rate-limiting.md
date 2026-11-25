# ADR-014: Rate limiting above the gRPC services

**Status:** Proposed

ADR-012 asks for rate limits on the calls that are open to the world. Doing it inside the services would scatter counters over the code, so it is a cross-cutting concern above them, switched on per RPC.

## Decision

- **Annotation driven.** `@RateLimited("policy")` on the method of a `*GrpcService` (`app/ratelimit`). Methods without it are never limited. At startup `RateLimitPolicies` scans the `BindableService` beans, maps the Java method name (lowerCamelCase of the RPC) to the full gRPC method name once, and fails the start if an annotation names an unknown policy or sits on a method that is no RPC. Per call there is only a map lookup.
- **One global interceptor** (`@GlobalServerInterceptor`) ordered right after Spring Security's `AuthenticationProcessInterceptor` (`CONTEXT_FILTER_ORDER - 5` against `- 10`; the first interceptor in order is the outermost). This deviates from "before authentication for the public calls": one interceptor after authentication serves both kinds. The public calls (register, login, refresh) pass authentication without a token and are keyed by peer anyway; the calls that need a user get one in the security context, and a flood of calls with bad or missing tokens is refused by authentication (cheap, no database) before it touches a counter. A test pins the ordering.
- **Counted at the first message**, not at call start, because the request body may be a key. A rejected call never reaches the handler. All current RPCs are unary; for streaming RPCs a call counts once.
- **Policies are configuration**: `exochat.ratelimit.policies.<name>` with `limit`, `window`, `key` (`PEER`, `USER`, `PEER_AND_USER`) and `request-key-limit`. The built-in policies ship in code and a configured entry overrides them field by field; a new name needs all fields. `exochat.ratelimit.enabled=false` switches everything off.
- **Fixed window counters.** Simple, cheap, and what Valkey `INCR` + `EXPIRE` does too. A caller can use the limit twice around a window boundary; fine for abuse protection.
- **Storage** behind `RateLimitStore.tryAcquire(key, limit, window)`. The in-memory implementation is a Caffeine cache with a maximum size (`max-keys`, 100 000) and per-entry expiry at the end of the window, atomic through `compute`, and driven by the injected `Clock`. Keys contain no personal data. A flood of distinct keys evicts counters instead of growing the heap; the price is that an attacker with a huge number of addresses could reset someone else's counter, which is why the bound is generous. A Valkey store is a second bean implementing the interface (the in-memory bean backs off with `@ConditionalOnMissingBean`); it is needed once there is more than one instance and is deferred as in ADR-012.
- **Keys and trust.** `PEER` is the address of the socket (IPv6: the /64 prefix). `X-Forwarded-For` and friends are ignored unless the operator names a header in `exochat.ratelimit.trusted-proxy-header`; then the last entry is used (the one the proxy appended, the earlier ones are client controlled), and only if it is an IP literal, otherwise the socket address is used. Set it only when every request passes the proxy. Behind a proxy without it, all clients share the proxy's counter. `USER` is the user id of the `AuthenticatedUser` in the security context; a call without one is rejected `UNAUTHENTICATED` (fail closed, never a shared bucket).
- **Second key from the body.** `RequestKeyExtractor<T>` beans, by request type, give a second counter per policy (`request-key-limit`, same window). `auth` ships the email address (trimmed, lower case) for `AuthenticateUserRequest` and `RegisterUserRequest`; only `login` uses it by default. The extractor is small enough not to special-case anything, and it stops password guessing against one account from many addresses. It is hashed (SHA-256) before it reaches the store or any log. Trade-off: anyone can use up the counter of a victim's email and make that person wait at most one window (15 minutes); the limits are chosen so that this costs a few minutes at worst and the victim's own session tokens keep working.
- **Rejection:** `RESOURCE_EXHAUSTED`, the message `too many requests` (no key, no limit) and a `retry-after` trailer with the whole seconds until the reset, at least 1. Logged at WARN with policy and method only, no address, email or token.
- **Store failure fails open** by default (`exochat.ratelimit.fail-open`, set false to fail closed): rate limiting is abuse mitigation and a broken counter store should not take login and chat down. The in-memory store cannot fail; this matters for a remote one. The error is logged.

## Built-in policies

| Policy | Limit | Window | Key | RPCs |
|---|---|---|---|---|
| `register` | 20 | 1h | PEER | `AuthService.RegisterUser` |
| `login` | 30, and 10 per email | 5m | PEER (+ email) | `AuthService.AuthenticateUser` |
| `refresh` | 60 | 1m | PEER | `AuthService.RefreshSession` |
| `change-password` | 5 | 15m | USER | `AuthService.ChangePassword` |
| `switch-account` | 30 | 1m | USER | `AuthService.SwitchAccount` |
| `user-update` | 30 | 1m | USER | `UserService.UpdateUser` |
| `user-delete` | 3 | 1h | USER | `UserService.DeleteUser` |
| `account-create` | 10 | 1h | USER | `AccountService.CreateAccount` |

A person who mistypes a password a few times stays far below 10 per email and window and is never locked out for more than a window; a script is stopped after a handful of tries. The peer limits are generous because a household or an office shares one address.

## Consequences

- Counters are per instance until a shared store exists; with N instances the effective limit is up to N times higher.
- Fixed windows allow short bursts at the boundary.
- `exochat.ratelimit.*` and the annotations are the whole surface: no changes to the services' code beyond the annotation.
- New RPCs are unlimited until annotated. Review new unauthenticated or expensive calls for it.
