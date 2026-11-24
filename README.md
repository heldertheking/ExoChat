# ExoChat

> ⚠️ **Early development.** ExoChat is in the planning/foundation stage. There is no runnable product yet; APIs, architecture and dates will change.

**An open, programmable communication platform with hosted infrastructure.**

ExoChat aims to make communities more than chat rooms: a flexible home for conversations, voice, activities, integrations and community-built experiences.

## Product principles

1. **Communication first** — fast, reliable messaging and synchronization.
2. **Open by design** — documented APIs, portable data and an extension ecosystem.
3. **Composable communities** — spaces can host different kinds of experiences.
4. **Safe extensibility** — explicit capabilities, sandboxing and scoped permissions.
5. **Solo-founder sustainable** — modular monolith first; extract services only when evidence justifies it.

## Current product scope

### MVP
- Accounts, profiles and sessions
- Communities, memberships, roles and permissions
- Text spaces and direct/group conversations
- Messages, edits, deletes, reactions and attachments
- Realtime delivery, presence and typing indicators
- Offline recovery and cursor-based history synchronization
- Basic moderation and in-app notifications

### After MVP
- Extension manifests and permission grants
- First-party extensions and custom commands
- Activities and custom widgets
- External integration delivery
- Voice rooms and screen sharing
- Desktop and mobile clients

## Technology baseline

| Area | Initial choice |
|---|---|
| Backend | Java 25 + Spring Boot |
| Database access | jOOQ |
| Database | MariaDB 11.4 + Liquibase (see ADR-009) |
| API contracts | Protocol Buffers |
| Service API | gRPC where appropriate |
| Browser realtime | WebSocket |
| Events | RabbitMQ + transactional outbox |
| Ephemeral state | Valkey |
| Web client | React + TypeScript |
| Desktop | Tauri (after web MVP) |
| Extension runtime | WASM, introduced after MVP |
| Observability | OpenTelemetry, Prometheus, Grafana |
| Deployment | Docker Compose initially |

## Repository map

- `docs/PRODUCT.md` — product definition, users and differentiators
- `docs/ARCHITECTURE.md` — service boundaries and architecture diagram
- `docs/DOMAIN_MODEL.md` — core domain entities and data ownership
- `docs/API_AND_EVENTS.md` — API, event envelope and delivery guarantees
- `docs/EXTENSIONS.md` — extension platform and security model
- `docs/NEW_MODULE.md` — how to add a Gradle module
- `docs/OPERATIONS_SECURITY.md` — deployment, reliability and security
- `docs/adr/` — architecture decision records (one file per ADR)
- `docs/planning/ROADMAP.md` — phased roadmap and exit criteria
- `docs/planning/BACKLOG.md` — starter engineering backlog
- `docs/planning/ROADMAP.ics` — proposed milestone calendar

## Code layout and quick start

Gradle multi-module build (Java 25, Spring Boot 4). Each module has its own README.

- [`app/`](app/README.md): Spring Boot application (gRPC server)
- [`libs/api/`](libs/api/README.md): resource name classes for the API objects
- [`libs/proto/`](libs/proto/README.md): protobuf contracts and gRPC stubs
- [`libs/db/`](libs/db/README.md): Liquibase changelogs and generated jOOQ code
- [`libs/query-language/`](libs/query-language/README.md): filter / order-by / pagination language on top of jOOQ
- [`libs/resource-name-utils/`](libs/resource-name-utils/README.md): AIP-122 resource names
- [`exochat-bom/`](exochat-bom/README.md): dependency platform
- [`docker/`](docker/README.md): local MariaDB

```
docker compose up -d
./gradlew build        # app tests need the compose MariaDB
./gradlew :app:bootRun
./gradlew coverageReport   # tests + combined JaCoCo report
```

### Code coverage

JaCoCo is applied to every Java module from the root `build.gradle`; no per-module setup is needed.

- Per module (runs after `test`): `<module>/build/reports/jacoco/test/html/index.html` and `jacocoTestReport.xml`.
- Whole build: `./gradlew coverageReport` writes `build/reports/jacoco/aggregate/html/index.html` and `aggregate.xml` (the XML is what CI or SonarQube/Codecov should consume).
- Generated code (jOOQ, protobuf/gRPC stubs, ANTLR parsers) is excluded via `coverageExcludes` in the root `build.gradle`. Add patterns there when a new generator is introduced.
- Modules without tests (`libs/db`, `libs/proto`) produce no report. The JaCoCo version is `jacoco` in `gradle/libs.versions.toml`.

## Development philosophy

Build a vertical slice before expanding the architecture. The first meaningful milestone is two users exchanging a message in real time, with the message persisted and its domain event reliably published.

Do not start with Kubernetes, a fleet of microservices, voice/video, federation or a public extension marketplace.

## Status

**Early development — planning baseline, no implementation yet.** Current phase: Phase 0 (Foundation, December 2026). Dates are planning estimates and will be adjusted based on available development time and feedback from early communities.

## Documentation

- [Product](docs/PRODUCT.md) · [Architecture](docs/ARCHITECTURE.md) · [Domain model](docs/DOMAIN_MODEL.md)
- [API and events](docs/API_AND_EVENTS.md) · [Extensions](docs/EXTENSIONS.md) · [Operations and security](docs/OPERATIONS_SECURITY.md)
- [Architecture decision records](docs/adr/README.md) · [Adding a module](docs/NEW_MODULE.md)

## Roadmap

See [docs/planning/ROADMAP.md](docs/planning/ROADMAP.md) for phases and exit criteria, [docs/planning/BACKLOG.md](docs/planning/BACKLOG.md) for the starter backlog and [docs/planning/ROADMAP.ics](docs/planning/ROADMAP.ics) for an importable calendar. Phases map 1:1 to GitHub milestones; work is tracked on the **ExoChat — Development** GitHub Project.

## Contributing

The project is in early development and the architecture is still settling, so the most useful contributions right now are feedback on the design docs and ADRs.

1. Browse [open issues](../../issues) — look for `type:research` and `type:documentation` first.
2. Open an issue (bug report or feature request template) before starting significant work.
3. Keep changes small and focused; reference the issue in your PR.
