# libs/api

Home of the **resource name classes** for ExoChat's API objects (`UserName`, later `CommunityName`, `ChannelName`, ...). Currently empty: only the folder structure and build file exist.

## Purpose

gRPC messages carry `name` and `parent` as [AIP-122](https://google.aip.dev/122) resource name strings (see `libs/proto`). This module turns those strings into typed classes, built on `libs/resource-name-utils`, so services and repositories never parse or concatenate names by hand.

## Layout

```
src/main/java/com/heldertheking/exochat/api/<domain>/<Thing>Name.java
src/test/java/com/heldertheking/exochat/api/<domain>/<Thing>NameTest.java
```

Use the same `<domain>` names as the proto packages (`user`, `health`, ...).

## Conventions (when adding the first names)

- Extend the base classes from `libs/resource-name-utils` (`AbstractChildResourceName` / `AbstractSubResourceName`), with a `fromName(String)` factory so they work with `ResourceNames.match(...)`.
- The persistence id (`getPersistenceId()`) is the id type used in `libs/db`, e.g. `VARCHAR(64)` for `user.id`.
- One test per name class: parse, format, wildcard, invalid input.
- Keep this module free of Spring, jOOQ and generated proto code. It depends only on `libs/resource-name-utils` (exposed as `api`) and jSpecify.

## Build and test

`./gradlew :libs:api:test`. Remove the `.gitkeep` files as soon as real classes exist.
