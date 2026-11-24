# Operations, Reliability and Security

## Initial hosting

Use Docker Compose for local development and the first small hosted environment. Prefer managed MariaDB and object storage when the cost is reasonable.

Initial runtime:
- Reverse proxy / TLS termination
- Spring Boot API
- Background worker
- MariaDB
- RabbitMQ
- Valkey
- S3-compatible object storage

Do not introduce Kubernetes until deployment scale and operational needs justify it.

## Observability

Instrument with OpenTelemetry. Carry trace and correlation IDs across RPC calls and event envelopes.

Track:
- API latency p50/p95/p99
- Message commit latency
- Recipient delivery latency
- WebSocket connection count
- Reconnect rate and recovery success
- RabbitMQ queue depth and oldest-message age
- Outbox backlog
- Consumer retry and dead-letter counts
- MariaDB query latency and connection pool saturation
- Error rate and resource utilization

## Security baseline

- TLS for all external and internal network paths.
- Short-lived access tokens and refresh-token rotation.
- Per-device sessions and revocation.
- Argon2id or a reputable identity provider for credentials.
- Server-side authorization on every protected operation.
- Per-user, per-IP and per-community rate limits.
- Signed object-storage upload/download URLs.
- Secret management; no credentials in source control.
- Automated backups and tested restore procedures.
- Durable audit records for security-sensitive administrative actions.
- Extension sandboxing and capability-based access.

## Privacy

Do not treat transport encryption or database encryption as end-to-end encryption. Do not claim E2EE until multi-device key management, recovery, verification and threat modeling are implemented and independently reviewed.

Keep private message content out of broadly accessible community event feeds and extension payloads.

## Reliability patterns

- Transactional outbox
- Idempotent consumers
- Bounded retries and dead-letter queues
- Cursor-based reconnect synchronization
- Per-conversation sequence allocation
- Backpressure on realtime delivery
- Graceful degradation when non-critical consumers are unavailable
- Backup restore drills

## Performance targets for early testing

These are goals for controlled test environments, not universal guarantees:
- Optimistic local message display: under 50 ms
- Server acknowledgement: p95 under 150 ms
- Online recipient delivery: p95 under 250 ms
- Presence propagation: p95 under 500 ms

Measure first; optimize the actual bottlenecks.
