# libs/api

Home of the **resource name classes** for ExoChat's API objects (`UserName`, later `CommunityName`, `ChannelName`, ...). 

## Purpose

gRPC messages carry `name` and `parent` as [AIP-122](https://google.aip.dev/122) resource name strings (see `libs/proto`). This module turns those strings into typed classes, built on `libs/resource-name-utils`, so services and repositories never parse or concatenate names by hand.

## Names

| Class | Pattern | Persistence id |
|---|---|---|
| `user.UserName` | `users/{uuidv7}` (`users/-` is the wildcard) | `UUID` (v7), the `user.id` column |

```java
UserName.of(UuidV7.next());           // users/0190a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b
UserName.fromName("users/0190a1b2-...");  // strict: lowercase UUIDv7 only, else ValidationException (an IllegalArgumentException)
UserName.wildcard();                  // users/-
ResourceNames.match(s).when(UserName::fromName).orElseThrow();
```

## UuidV7

`id.UuidV7` generates RFC 9562 version 7 UUIDs: `UuidV7.next()` (shared, thread safe). Ids are strictly increasing (millisecond timestamp + counter, also if the clock steps back), so they sort in creation order and work as keyset pagination keys. Helpers: `isV7`, `timestamp(uuid)`, `parse(string)` (canonical lowercase only). **Generate every new primary key with it in the application, never `UUID.randomUUID()`.** Tests can use `new UuidV7(clock, random)`.

## Layout

```
src/main/java/com/heldertheking/exochat/api/<domain>/<Thing>Name.java
src/test/java/com/heldertheking/exochat/api/<domain>/<Thing>NameTest.java
```

Use the same `<domain>` names as the proto packages (`user`, `health`, ...).

## Conventions (when adding the first names)

- Extend the base classes from `libs/resource-name-utils` (`AbstractChildResourceName` / `AbstractSubResourceName`), with a `fromName(String)` factory so they work with `ResourceNames.match(...)`.
- The persistence id (`getPersistenceId()`) is the id type used in `libs/db`, e.g. `UUID` for `user.id`.
- One test per name class: parse, format, wildcard, invalid input.
- Keep this module free of Spring, jOOQ and generated proto code. It depends only on `libs/resource-name-utils` (exposed as `api`) and jSpecify.

## Build and test

`./gradlew :libs:api:test`. Use `user/UserName` as the template for new top-level names; for nested names extend `AbstractSubResourceName`.
