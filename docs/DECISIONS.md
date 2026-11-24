# Architecture Decision Records

## ADR-001: Modular monolith before microservices
**Status:** Accepted

Build domain modules in one Spring Boot deployment initially. Extract only when independent scaling, failure isolation or team ownership provides a measurable reason.

## ADR-002: Relational database is the source of truth
**Status:** Accepted (engine changed by ADR-009)

Persist authoritative users, communities, memberships, conversations and messages in the relational database. Keep ephemeral presence/cache data in Valkey.

## ADR-003: jOOQ for database access
**Status:** Accepted

Use SQL-oriented, type-safe access with generated schema metadata. Avoid a generic repository abstraction that hides important query behavior.

## ADR-004: Protobuf as canonical API schema
**Status:** Accepted

Version and lint protobuf contracts. Use native gRPC where supported; use browser-compatible transports where required.

## ADR-005: RabbitMQ for asynchronous event transport
**Status:** Accepted

Use domain events for decoupled consumers and integrations. RabbitMQ is not the permanent event archive.

## ADR-006: Transactional outbox
**Status:** Accepted

Write domain state and an outbox event in the same database transaction. Publish asynchronously with confirms and idempotent consumers.

## ADR-007: Rust gateway only after evidence
**Status:** Proposed

Start realtime delivery in the implementation language the solo developer can ship and operate most effectively. Consider Rust extraction after repeatable load tests identify a connection or fan-out bottleneck.

## ADR-008: Hybrid extension model
**Status:** Proposed

Use sandboxed WASM for bounded extension code and external services for advanced integrations. Public extension APIs must not expose internal infrastructure credentials.

## ADR-009: MariaDB and Liquibase for the initial implementation
**Status:** Accepted

The foundation uses MariaDB 11.4 with Liquibase migrations (generated jOOQ code is committed in `libs/db`) instead of the originally planned PostgreSQL + Flyway, matching tooling already familiar to the developer. This supersedes the engine named in ADR-002. jOOQ keeps the SQL layer portable enough to revisit if PostgreSQL-specific features become necessary.

## ADR-010: Shared list-query language (quela)
**Status:** Accepted

List endpoints use AIP-style `filter`, `order_by`, `page_size` and `page_token`, handled by `libs/query-language`: ANTLR-parsed filter and ordering turned into jOOQ conditions, with keyset page tokens bound to a checksum of the request. Repositories must not parse these themselves.
