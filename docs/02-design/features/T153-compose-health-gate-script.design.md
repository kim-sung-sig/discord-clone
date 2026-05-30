# T153 Compose Health Gate Script Design

Date: 2026-05-20
Slice: T153 Compose Health Gate Script

## Design

`qa/central-compose-health.ps1` is a local preflight script for central Docker resources.

## Resource Checks

- Postgres:
  - Existing container: `postgres-source`
  - Compose service: `postgres-source`
  - Readiness command: `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB`
- Redis:
  - Existing container: `ms-redis`
  - Compose service: `ms-redis`
  - Readiness command: `redis-cli -a $SPRING_DATA_REDIS_PASSWORD ping`
- Kafka:
  - Existing container: `ms-kafka`
  - Compose service: `ms-kafka`
  - Readiness check: TCP connect to `127.0.0.1:29092`

## Runtime Behavior

The script first checks for already-running central containers. If a resource is missing or unhealthy, the script starts the matching Compose service and waits up to 60 seconds.

This keeps the script compatible with the current developer machine, where central resources may already exist outside this repository's Compose project.
