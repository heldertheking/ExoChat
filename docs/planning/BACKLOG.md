# ExoChat Starter Backlog

Use these as initial GitHub issues. Keep tasks small enough to complete and verify independently.

## Foundation
- [x] Create monorepo and module conventions (`docs/NEW_MODULE.md`)
- [x] Add Spring Boot application and health endpoints (gRPC `HealthService`)
- [x] Configure MariaDB and Liquibase (ADR-009)
- [x] Configure jOOQ code generation
- [x] Add Protobuf build
- [ ] Add Protobuf lint and breaking-change check (buf)
- [x] Add Docker Compose for local dependencies
- [x] Add CI build and test workflow (`.github/workflows/ci.yml`, includes JaCoCo and a jOOQ drift check)
- [ ] Add OpenTelemetry instrumentation
- [ ] Add Actuator and Prometheus metrics endpoint

## Identity
- [ ] Define user and profile schema (`user` table done; accounts pending)
- [ ] Write authentication ADR (sessions vs tokens, per-device sessions, credential storage, how a request resolves to a `UserName`/`AccountName`)
- [ ] Define account schema and implement `AccountService` (create/update/delete)
- [ ] Implement registration
- [ ] Implement login and refresh-token rotation
- [ ] Implement per-device session listing and revocation
- [ ] Implement account switching for multiple accounts per user
- [ ] Add rate limits to authentication endpoints

## Communities and permissions
- [ ] Define community and membership schema
- [ ] Implement community creation
- [ ] Implement invite and join flow
- [ ] Define role and permission model
- [ ] Implement authorization interface
- [ ] Add private-space access tests

## Messaging
- [ ] Define conversation and message schema
- [ ] Implement idempotent SendMessage
- [ ] Implement cursor-based history API
- [ ] Implement edits, deletes and reactions
- [ ] Implement last-read sequence tracking
- [ ] Add transactional outbox
- [ ] Add RabbitMQ publisher confirms
- [ ] Add idempotent event consumer example
- [ ] Add retry and dead-letter handling

## Realtime
- [ ] Implement authenticated WebSocket connection
- [ ] Implement message fan-out
- [ ] Implement presence with Valkey TTL
- [ ] Implement typing indicators
- [ ] Implement reconnect checkpoint and catch-up
- [ ] Add backpressure and connection metrics

## Client
- [ ] Create React + TypeScript application
- [ ] Build authentication screens
- [ ] Build community navigation
- [ ] Build message timeline
- [ ] Add optimistic message rendering
- [ ] Add pending/failed message states
- [ ] Add offline and reconnect UX
- [ ] Add basic accessibility and keyboard navigation

## Operations and safety
- [ ] Configure automated database backups
- [ ] Test restore procedure
- [ ] Add rate limits and abuse reporting
- [ ] Add audit history for admin actions
- [ ] Add load test for message send and fan-out
- [ ] Document incident and rollback procedure

## Extensions — post-MVP
- [ ] Define extension manifest schema
- [ ] Define capability permission model
- [ ] Implement install/disable/revoke lifecycle
- [ ] Publish event contract catalogue
- [ ] Build polls extension
- [ ] Build developer SDK sample
- [ ] Prototype sandboxed WASM runtime
