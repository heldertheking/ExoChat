# libs/resource-name-utils

Typed helpers for [AIP-122](https://google.aip.dev/122) resource names such as `pools/1/employees/15`. Used for the `name` and `parent` fields in gRPC messages. Published as a library (`com.heldertheking:resource-name-utils`, `0.1.0-SNAPSHOT`, `maven-publish`, with sources and javadoc jars).

## Types

- `Name`: sealed root. `ResourceName<ID>`: a name with a persistence id (`getPersistenceId()`), wildcard support (`-`, `isWildcard()`) and `getTemplateVariables()`.
- `ChildResourceName` / `SubResourceName` model nesting; `AbstractChildResourceName` / `AbstractSubResourceName` are the base classes to extend.
- `ResourceNames.match(string)` tries several types in order (`.when(EmployeeName::fromName, ...)`) instead of string sniffing; see its Javadoc.
- `ResourceNotFoundException` for names that parse but do not exist.

Built on `com.google.api:api-common` `PathTemplate`. Packages are `@NullMarked`.

## Build and test

`./gradlew :libs:resource-name-utils:test`. ErrorProne + NullAway run as errors in main code. Pure Java, no Spring.
