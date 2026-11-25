# libs/api

Home of the **resource name classes** for ExoChat's API objects (`UserName`, `ServerName`, `ChannelName`, ...). Packages mirror the proto domains: `identity`, `community`, `messaging`.

## Purpose

gRPC messages carry `name` and `parent` as [AIP-122](https://google.aip.dev/122) resource name strings (see `libs/proto`). This module turns those strings into typed classes, built on `libs/resource-name-utils`, so services and repositories never parse or concatenate names by hand.

## Names

All ids are UUIDv7 (ADR-011); `-` in the last segment is the wildcard (a wildcard parent requires a wildcard child).

| Package | Class | Pattern |
|---|---|---|
| `identity` | `UserName` | `users/{user}` |
| `identity` | `AccountName` | `users/{user}/accounts/{account}` |
| `identity` | `RelationshipName` | `users/{user}/accounts/{account}/relationships/{relationship}` |
| `community` | `ServerName` | `servers/{server}` |
| `community` | `MemberName` | `servers/{server}/members/{member}` |
| `community` | `RoleName` | `servers/{server}/roles/{role}` |
| `community` | `InviteName` | `servers/{server}/invites/{invite}` |
| `messaging` | `ChannelName` | `channels/{channel}` (canonical; the proto also accepts the alias `servers/{server}/channels/{channel}`, not modelled here yet) |
| `messaging` | `MessageName` | `channels/{channel}/messages/{message}`: the only way to address a message |
| `messaging` | `ChannelParticipantName` | `channels/{channel}/participants/{participant}` (direct channels) |
| `messaging` | `ChannelOverwriteName` | `channels/{channel}/overwrites/{overwrite}` (server channels) |

Only the names exist so far, not the domain objects. A member or participant refers to its account with the string `users/{user}/accounts/{account}` (parse it with `AccountName.fromName`); that is a reference, not part of the member name.

```java
var user = UserName.of(UuidV7.next());
var account = AccountName.of(user, UuidV7.next());   // users/<uuid>/accounts/<uuid>
AccountName.fromName(account.toString());            // strict: lowercase UUIDv7 only, else ValidationException
AccountName.wildcard(UserName.wildcard());           // users/-/accounts/-
ResourceNames.match(s).when(UserName::fromName).when(ServerName::fromName).orElseThrow();
```

Top-level names extend `id.AbstractUuidResourceName`, nested names `id.AbstractUuidSubResourceName` (both enforce UUIDv7 and do the parsing). To add a name, copy a similar class: the template, the variable name, `of`, `wildcard` and `fromName`.

## UuidV7

`id.UuidV7` generates RFC 9562 version 7 UUIDs: `UuidV7.next()` (shared, thread safe). Ids are strictly increasing (millisecond timestamp + counter, also if the clock steps back), so they sort in creation order and work as keyset pagination keys. Helpers: `isV7`, `timestamp(uuid)`, `parse(string)` (canonical lowercase only). **Generate every new primary key with it in the application, never `UUID.randomUUID()`.** Tests can use `new UuidV7(clock, random)`.

## Layout

```
src/main/java/com/heldertheking/exochat/api/<domain>/<Thing>Name.java
src/test/java/com/heldertheking/exochat/api/<domain>/<Thing>NameTest.java
```

Use the same `<domain>` names as the proto packages (`identity`, `community`, `messaging`, `health`, ...).

## Conventions (when adding the first names)

- Extend the base classes from `libs/resource-name-utils` (`AbstractChildResourceName` / `AbstractSubResourceName`), with a `fromName(String)` factory so they work with `ResourceNames.match(...)`.
- The persistence id (`getPersistenceId()`) is the id type used in `libs/db`, e.g. `UUID` for `user.id`.
- One test per name class: parse, format, wildcard, invalid input.
- Keep this module free of Spring, jOOQ and generated proto code. It depends only on `libs/resource-name-utils` (exposed as `api`) and jSpecify.

## Build and test

`./gradlew :libs:api:test`. Use `identity/UserName` (top-level) and `identity/AccountName` (nested) as templates for new names.
