# ADR-013: Layered allow/deny rules for permissions

**Status:** Proposed

What an account may do is decided by named **rules** (`CREATE_SERVER`, `SEND_MESSAGES`, `MANAGE_ROLES`, ...) that layers allow or deny, not by a bitmask or by global role strings. The `PermissionEvaluator` and per-domain `*PermissionChecker`s follow app-gateway; the access token carries identity only, so a change applies at once.

## Decision

- **Rule.** An action with a default: most rules are allowed by default, management rules are denied. The same names exist as the `Rule` enum of `common/v1alpha1/rule.proto`, as the Java `Rule` enum and as strings in the database; `ProtoEnumSyncTest` keeps the first two in sync, unknown strings from the database are ignored.
- **State.** `ALLOW` or `DENY`. A layer that has no opinion has no row: the rule is inherited. There is no stored `INHERIT`.
- **Layers, general to specific.**
  1. *Platform:* the `standing` of the account (`MEMBER`, `RESTRICTED`, `SUSPENDED`) gives a base rule set (`MEMBER` none, `RESTRICTED` a general limited set, `SUSPENDED` everything denied), refined by rules the platform set on the account (`account_rule`). A `DENY` here is a **ceiling**: no server, role or channel can lift it. An `ALLOW` here only lifts a denial of the standing.
  2. *Server owner:* allowed everything in their own server, still under the ceiling.
  3. *Server roles:* the roles of the account (including the default role of the server); the role with the highest `position` that decides the rule wins, on equal positions the denial wins.
  4. *Channel:* overwrites of roles (highest position wins), then the overwrite of the account itself; each replaces the decision before it.
  5. *Default of the rule.*
- **Where it lives.** `app/permission` is a pure package (`Rule`, `RuleState`, `RuleSet`, `Standing`, `PermissionInput`, `PermissionResolver`, the `PermissionRepository` interface) with no Spring, jOOQ or gRPC types; the jOOQ repository is in `permission.persistence`. `PermissionEvaluator` (in `security`) bridges the authenticated caller and the repository and takes an explicit subject (`can(accountName, rule)`) so that code without a security context, for example an event consumer, can ask as well. When a second module needs the package it moves to a library unchanged.
- **Protos.** `Account.standing` (output only), `Role.rules` and `ChannelOverwrite.rules` are `repeated RuleSetting {rule, state}`. The earlier `int64` bitmasks are gone.
- **First use.** `CREATE_ACCOUNT` guards `AccountService.CreateAccount` (a `RESTRICTED` account may not create accounts); the other rules wait for the services that use them.
- **Scope.** Rules are scope-free actions; the channel scope (direct, group, server) is context. A rule that only makes sense in some channels (such as `SPEAK`) is simply not used elsewhere.

## Consequences

- No 64 rule limit, rules are readable in the database and in audits, and extension defined rules (ADR-008) can later use namespaced string names.
- Resolving a rule costs a few queries per check. The repository interface is the place for a short lived cache; it is not added until measured. Standing and rule changes must stay visible within seconds, so a cache needs explicit invalidation.
- Only the platform layer is loaded today. The server and channel layers (`role_rule`, `channel_overwrite_rule`) arrive with the community and messaging tables; the resolver and its tests already define their semantics.
