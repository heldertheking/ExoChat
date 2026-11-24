# ADR-009: MariaDB and Liquibase for the initial implementation

**Status:** Accepted

The foundation uses MariaDB 11.4 with Liquibase migrations (generated jOOQ code is committed in `libs/db`) instead of the originally planned PostgreSQL + Flyway, matching tooling already familiar to the developer. This supersedes the engine named in ADR-002. jOOQ keeps the SQL layer portable enough to revisit if PostgreSQL-specific features become necessary.
