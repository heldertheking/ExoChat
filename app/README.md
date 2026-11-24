# app

The ExoChat Spring Boot application (modular monolith, ADR-001). Currently a skeleton: a gRPC server with a health service and a jOOQ/Liquibase-backed datasource.

## Run

```
docker compose up -d          # MariaDB on :3308 (see docker/README.md)
./gradlew :app:bootRun        # gRPC on :9090, Liquibase migrates on start
```

Config is in `src/main/resources/application.yml` and can be overridden with `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`. The entry point is `ExoChatApplication`; gRPC services are Spring beans (see `health/HealthGrpcService`).

## Test

`./gradlew :app:test` needs the compose MariaDB running. Tests use the `exochat_test` database (`src/test/resources/application.yml`, gRPC port 0, small Hikari pool) and never touch dev data.

DB test pattern (see `user/UserTableTest`): a narrow `@SpringBootTest` context importing only the DataSource, Liquibase and jOOQ autoconfigs, plus `@Sql` loading `src/test/resources/test-data/<area>/test-dataset.sql` before each test. For gRPC services, unit test the service class directly like `HealthGrpcServiceTest`.

## Depends on

`libs:api` (resource names, `UuidV7`), `libs:proto` (contracts), `libs:db` (schema + jOOQ), `libs:query-language` (list endpoints), `exochat-bom`. `libs:resource-name-utils` comes in transitively through `libs:api`.

## Adding a feature

1. Define the contract in `libs/proto`.
2. Add the table in `libs/db` and regenerate jOOQ.
3. Create package `com.heldertheking.exochat.app.<domain>` with a jOOQ repository (no generic repository abstraction, ADR-003) and a gRPC service.
4. For list endpoints, use a quela filter and `ListPage`.
5. Add tests as above.
