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
| Database | PostgreSQL + Flyway |
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
- `docs/OPERATIONS_SECURITY.md` — deployment, reliability and security
- `docs/DECISIONS.md` — initial architecture decision records
- `planning/ROADMAP.md` — phased roadmap and exit criteria
- `planning/BACKLOG.md` — starter engineering backlog
- `planning/ROADMAP.ics` — proposed milestone calendar

## Development philosophy

Build a vertical slice before expanding the architecture. The first meaningful milestone is two users exchanging a message in real time, with the message persisted and its domain event reliably published.

Do not start with Kubernetes, a fleet of microservices, voice/video, federation or a public extension marketplace.

## Status

**Early development — planning baseline, no implementation yet.** Current phase: Phase 0 (Foundation, December 2026). Dates are planning estimates and will be adjusted based on available development time and feedback from early communities.

## Documentation

- [Product](docs/PRODUCT.md) · [Architecture](docs/ARCHITECTURE.md) · [Domain model](docs/DOMAIN_MODEL.md)
- [API and events](docs/API_AND_EVENTS.md) · [Extensions](docs/EXTENSIONS.md) · [Operations and security](docs/OPERATIONS_SECURITY.md)
- [Architecture decision records](docs/DECISIONS.md)

## Roadmap

See [planning/ROADMAP.md](planning/ROADMAP.md) for phases and exit criteria, [planning/BACKLOG.md](planning/BACKLOG.md) for the starter backlog and [planning/ROADMAP.ics](planning/ROADMAP.ics) for an importable calendar. Phases map 1:1 to GitHub milestones; work is tracked on the **ExoChat — Development** GitHub Project.

## Contributing

The project is in early development and the architecture is still settling, so the most useful contributions right now are feedback on the design docs and ADRs.

1. Browse [open issues](../../issues) — look for `type:research` and `type:documentation` first.
2. Open an issue (bug report or feature request template) before starting significant work.
3. Keep changes small and focused; reference the issue in your PR.
