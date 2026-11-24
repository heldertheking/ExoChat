# ADR-006: Transactional outbox

**Status:** Accepted

Write domain state and an outbox event in the same database transaction. Publish asynchronously with confirms and idempotent consumers.
