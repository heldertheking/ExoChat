# Architecture Decision Records

## ADR-001: Modular monolith before microservices
**Status:** Accepted

Build domain modules in one Spring Boot deployment initially. Extract only when independent scaling, failure isolation or team ownership provides a measurable reason.

## ADR-002: PostgreSQL is the source of truth
**Status:** Accepted

Persist authoritative users, communities, memberships, conversations and messages in PostgreSQL. Keep ephemeral presence/cache data in Valkey.

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
