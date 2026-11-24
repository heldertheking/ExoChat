## Summary

<!-- What changes and why. Link the issue: Closes #123 -->

## Checklist

- [ ] `./gradlew build` passes locally (needs `docker compose up -d`)
- [ ] Schema changes include a Liquibase changeset and regenerated jOOQ sources (`./gradlew :libs:db:jooqCodegen`)
- [ ] Proto changes are backwards compatible (or the package version is bumped)
- [ ] Docs/ADRs updated where behaviour or decisions changed
