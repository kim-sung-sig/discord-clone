# T144 Central Runtime Resource Profiles Design

Date: 2026-05-19
Slice: T144 Central Runtime Resource Profiles

## Architecture

The runtime baseline stays profile-driven:

- `postgres` owns JDBC and Flyway migration settings.
- `redis` owns Spring Redis connection settings.
- `kafka` owns Kafka bootstrap and topic-prefix settings.

This keeps unit-test in-memory adapters available while making integration and smoke runs prefer central infrastructure through `SPRING_PROFILES_ACTIVE=postgres,redis,kafka`.

## Resource Defaults

| Profile | Resource | Default |
| --- | --- | --- |
| `postgres` | `spring.datasource.url` | `jdbc:postgresql://127.0.0.1:15432/discord` |
| `postgres` | `spring.flyway.locations` | `classpath:db/migration` |
| `postgres` | `spring.flyway.baseline-on-migrate` | `true` |
| `redis` | `spring.data.redis.port` | `16379` |
| `kafka` | `spring.kafka.bootstrap-servers` | `127.0.0.1:29092` |

## Data Flow

Spring Boot loads the selected profiles from resources. `PostgresPersistenceConfiguration` reads the datasource and Flyway properties from the environment, creates the Postgres `DataSource`, and builds the `postgresFlyway` bean from resource-backed values.

Nuxt security telemetry uses Postgres for CSP telemetry and Redis for CSP report rate limiting through `.env.example`.

## Testing

- A PowerShell resource contract checks the expected profile files and endpoint values.
- `PersistenceBootstrapTest` verifies Flyway uses the resource-defined migration location and baseline behavior while creating core tables against Postgres.

## Error Handling

The profile files remain environment-overridable. If a developer has different ports, they can override `POSTGRES_JDBC_URL`, `SPRING_DATA_REDIS_PORT`, or `SPRING_KAFKA_BOOTSTRAP_SERVERS` without editing resources.
