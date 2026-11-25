# libs/proto

Protocol Buffers contracts and generated gRPC/Java stubs. Protobuf is the canonical API schema (ADR-004).

## Layout

`src/main/proto/com.heldertheking.exochat/<domain>/<version>/*.proto`, package `com.heldertheking.exochat.<domain>.<version>`, Java package the same, `java_multiple_files = true`.

| Domain | Status |
|---|---|
| `health/v1alpha1` | `HealthService.Check` (implemented in `app`) |
| `identity/v1alpha1` | `User`, `Account`, `Relationship` (contract only) |
| `community/v1alpha1` | `Server`, `Member`, `Role`, `Invite` (contract only) |
| `messaging/v1alpha1` | `Channel` (two names), `ChannelParticipant`, `ChannelOverwrite`, `Message` (contract only) |
| `common/v1alpha1` | `Reference` |
| `auth/v1alpha1` | placeholder |

## Conventions

- Resource oriented, Google AIP style: `name`/`parent` are resource names (see `libs/resource-name-utils`); list calls use `filter`, `order_by`, `page_size`, `page_token` (see `libs/query-language`).
- New APIs start at `v1alpha1`; breaking changes need a new version package.
- Validation via protovalidate annotations. User names are `users/{uuidv7}` (lowercase canonical form, see `libs/api` `UserName`).

## Build

`./gradlew :libs:proto:generateProto` (or any compile). Output lands in `build/` and is not committed. protoc `4.33.4`, grpc-java `1.77.1` (see `gradle/libs.versions.toml`). The module exposes the `grpc` dependency bundle as `api`, so consumers get stubs and protovalidate.
