# docker

Local infrastructure, started from the repo root with `docker compose up -d`.

- `mariadb` (11.4, container `exochat-mariadb`, host port **3308**). Volume `exochat-mariadb-data`.
- `mariadb/init/01-databases.sql` runs only on the first start of an empty volume. It creates `exochat` (dev) and `exochat_test` (tests) and the user `exochat` / `exoch4t`.

`docker compose down` keeps data; `docker compose down -v` wipes it and reruns the init script. Credentials are for local development only.
