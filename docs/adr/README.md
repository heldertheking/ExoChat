# Architecture Decision Records

One file per decision, named `NNN-short-title.md`. Never rewrite history: to change a decision, add a new ADR and mark the old one `Superseded by ADR-NNN` (or note the change in its status).

## Template

```
# ADR-NNN: Title

**Status:** Proposed | Accepted | Superseded by ADR-NNN

Context, the decision, and the consequences, in a few short paragraphs.
```

## Index

| ADR | Decision | Status |
|---|---|---|
| [001](001-modular-monolith-before-microservices.md) | Modular monolith before microservices | Accepted |
| [002](002-relational-database-is-the-source-of-truth.md) | Relational database is the source of truth | Accepted (engine changed by ADR-009) |
| [003](003-jooq-for-database-access.md) | jOOQ for database access | Accepted |
| [004](004-protobuf-as-canonical-api-schema.md) | Protobuf as canonical API schema | Accepted |
| [005](005-rabbitmq-for-asynchronous-event-transport.md) | RabbitMQ for asynchronous event transport | Accepted |
| [006](006-transactional-outbox.md) | Transactional outbox | Accepted |
| [007](007-rust-gateway-only-after-evidence.md) | Rust gateway only after evidence | Proposed |
| [008](008-hybrid-extension-model.md) | Hybrid extension model | Proposed |
| [009](009-mariadb-and-liquibase-for-the-initial-implementation.md) | MariaDB and Liquibase for the initial implementation | Accepted |
| [010](010-shared-list-query-language-quela.md) | Shared list-query language (quela) | Accepted |
| [011](011-uuidv7-primary-keys.md) | UUIDv7 primary keys | Accepted |
| [012](012-own-jwt-authentication.md) | Own JWT authentication with rotating refresh tokens | Proposed |
| [013](013-layered-rules-for-permissions.md) | Layered allow/deny rules for permissions | Proposed |
| [014](014-rate-limiting.md) | Rate limiting above the gRPC services | Proposed |
