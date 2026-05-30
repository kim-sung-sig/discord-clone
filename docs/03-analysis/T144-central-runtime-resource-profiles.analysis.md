# T144 Central Runtime Resource Profiles Analysis

Date: 2026-05-19
Slice: T144 Central Runtime Resource Profiles

## Analysis

The project had moved several critical paths to Postgres and Redis, but the documented local defaults still implied mixed behavior: some runs used central Postgres, Redis defaulted to `6379`, Kafka had no Spring profile, and Nuxt CSP rate limiting still defaulted to in-memory unless manually configured.

This slice aligns resource configuration with the prepared Docker Desktop resources without deleting in-memory adapters that are still useful for fast unit tests. The next durable step is replacing remaining runtime in-memory adapters behind Postgres/Redis/Kafka profiles and adding smoke tests for those profiles.

## Trade-Offs

- Kafka is configured as a resource profile before adding Kafka producers or consumers.
- In-memory adapters remain available for unit tests and profile omissions.
- Docker Compose is not rewritten yet because the user-provided Docker resources already exist and have a different topology from the older compose file.

## Security Notes

- The checked-in credentials remain local development defaults only.
- Production must keep using non-local secrets and hosts through `production,postgres,redis,kafka`.
- The central resource document avoids introducing production credentials.

## Residual Risk

Some runtime beans still fall back to in-memory when profiles are omitted. Follow-up tasks now track removal or fail-closed behavior for production-like runs.
