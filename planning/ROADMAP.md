# ExoChat Development Roadmap

**Planning baseline:** 23 November 2026  
**Assumption:** one developer, substantially full-time. Part-time development may extend this plan to 12–18+ months.

Dates are planning windows, not promises.

## Phase 0 — Product and technical foundation
**Target:** December 2026

Deliver:
- Product requirements and MVP acceptance criteria
- Monorepo and CI
- Spring Boot application
- PostgreSQL, Flyway and jOOQ
- Protobuf toolchain
- Docker Compose
- Authentication skeleton
- Observability baseline

Exit criteria:
- Clean local setup from documented instructions
- Automated build and tests
- Authenticated API request works

## Phase 1 — Domain and messaging backend
**Target:** January–February 2027

Deliver:
- Users, profiles and sessions
- Communities and memberships
- Roles and authorization
- Spaces and conversations
- Message persistence and history
- Idempotent send API
- Transactional outbox
- RabbitMQ publisher and consumer

Exit criteria:
- Two accounts can create/join a community
- Message persistence survives restart
- Duplicate request does not duplicate message
- Outbox recovers after broker outage

## Phase 2 — Realtime web alpha
**Target:** March 2027

Deliver:
- React + TypeScript client
- Community and conversation navigation
- Message timeline
- WebSocket realtime delivery
- Presence and typing
- Cursor-based reconnect recovery
- In-app notifications

Exit criteria:
- Two users exchange messages live
- Offline client catches up after reconnect
- Permission checks work for private spaces

## Phase 3 — Private beta and hardening
**Target:** April 2027

Deliver:
- Moderation basics
- Rate limits and abuse controls
- Attachment uploads
- Error handling and retry UX
- Load tests
- Backup and restore drills
- Security review of authorization paths

Exit criteria:
- A small pilot community can use the product
- Critical data loss and access-control tests pass
- Operational dashboards and alerts exist

## Phase 4 — Extension foundations
**Target:** May–June 2027

Deliver:
- Extension manifest schema
- Extension installation records
- Capability and permission grants
- Event contract catalogue
- First-party polls and calendar extensions
- Developer SDK prototype

Exit criteria:
- Community admin can install/disable a first-party extension
- Extension receives only authorized events
- Permission revocation takes effect

## Phase 5 — Activities and developer preview
**Target:** July–August 2027

Deliver:
- Activity lifecycle
- Activity sessions
- Controlled widget components
- Extension storage
- Developer documentation and sample extension
- External integration delivery prototype

Exit criteria:
- A developer can build and test a sample extension from docs
- Activity can launch inside a community and handle participants

## Phase 6 — Public beta and desktop
**Target:** September–November 2027

Deliver:
- Desktop packaging with Tauri if web beta is stable
- Performance optimization based on telemetry
- Community onboarding and migration helpers
- Public developer preview
- Voice technical prototype only if core retention and reliability are healthy

Exit criteria:
- Public onboarding is repeatable
- Support, moderation and incident processes exist
- Core product reliability is measured in real use

## Explicitly deferred
- Full federation
- End-to-end encryption
- Native mobile apps
- Full video conferencing
- Public arbitrary-code marketplace
- Kubernetes and multi-region deployment

## Roadmap governance

At the end of each phase:
1. Review user feedback and operational metrics.
2. Re-estimate the next phase.
3. Close or rescope unfinished work.
4. Update ADRs when a technical decision changes.
5. Avoid adding features that do not support retention, reliability or the extension thesis.
