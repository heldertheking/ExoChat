# ADR-002: Relational database is the source of truth

**Status:** Accepted (engine changed by ADR-009)

Persist authoritative users, communities, memberships, conversations and messages in the relational database. Keep ephemeral presence/cache data in Valkey.
