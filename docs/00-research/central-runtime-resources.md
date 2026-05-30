# Central Runtime Resources

Date: 2026-05-19

## Local Docker Resources

| Resource | Container | Host Endpoint | Container Endpoint | Purpose |
| --- | --- | --- | --- | --- |
| PostgreSQL source | `postgres-source` | `127.0.0.1:15432` | `5432` | Primary application database and Flyway migration target |
| PostgreSQL replica | `postgres-replica` | `127.0.0.1:15433` | `5432` | Read replica or recovery drill target |
| Redis | `ms-redis` | `127.0.0.1:16379` | `6379` | Presence TTL, gateway Redis streams, backend rate limits |
| Kafka | `ms-kafka` | `127.0.0.1:29092` | `29092` | Future cross-node event fanout and async product events |

Reference compose path: `C:\git\chat-platform\docker\compose.yml`.

Note: the reference compose `.env` uses `POSTGRES_DB=messagesystem`, while this project currently targets the user-requested `discord` database. Keep `discord` for this workspace unless the database naming plan changes explicitly.

## Spring Profiles

Use central resources by default for local integration work:

```powershell
$env:SPRING_PROFILES_ACTIVE='postgres,redis,kafka'
$env:POSTGRES_JDBC_URL='jdbc:postgresql://127.0.0.1:15432/discord'
$env:POSTGRES_USER='dev_user'
$env:POSTGRES_PASSWORD='dev_password'
$env:SPRING_FLYWAY_LOCATIONS='classpath:db/migration'
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE='true'
$env:SPRING_DATA_REDIS_HOST='127.0.0.1'
$env:SPRING_DATA_REDIS_PORT='16379'
$env:SPRING_DATA_REDIS_PASSWORD='dev_password'
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:29092'
```

## Nuxt Runtime

Use centralized persistence and Redis-backed security controls:

```powershell
$env:NUXT_CSP_TELEMETRY_POSTGRES_URL='postgres://dev_user:dev_password@127.0.0.1:15432/discord'
$env:NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL='redis://:dev_password@127.0.0.1:16379'
```

## Policy

- New integration and smoke tasks should prefer `postgres,redis,kafka` over process-local in-memory defaults.
- Unit tests may keep in-memory adapters when the goal is pure domain behavior.
- Production must use `production,postgres,redis,kafka` with non-local credentials and non-local hosts.
- Flyway settings belong in `application-postgres.yml` and are consumed by the custom `postgresFlyway` bean.
- `infra/docker/docker-compose.yml` now mirrors the local central endpoints with `postgres-source`, `ms-redis`, and `ms-kafka`.
