# T144 Central Runtime Resource Profiles Report

Date: 2026-05-19
Slice: T144 Central Runtime Resource Profiles

## Summary

T144 aligned Spring and Nuxt local runtime defaults with the prepared Docker Desktop resources: Postgres on `15432`, Redis on `16379`, and Kafka on `29092`. Flyway settings are now declared in `application-postgres.yml` and consumed by the custom Flyway bean.

## Loop Result

Plan reviewed > implementation plan prepared > implementation completed > review completed > 27/30 PASS > next plan can proceed

## Implemented Changes

- Added `qa/central-runtime-resources.contract.ps1`.
- Added `backend/boot/src/main/resources/application-kafka.yml`.
- Updated `application-postgres.yml` with explicit Flyway resource settings.
- Updated `PostgresPersistenceConfiguration` to read Flyway resource properties.
- Updated `application-redis.yml` default port to `16379`.
- Added Redis password defaults for the referenced `ms-redis` container.
- Updated `.env.example` to prefer `postgres,redis,kafka`.
- Updated Nuxt CSP report limiter default to `redis://127.0.0.1:16379`.
- Updated Nuxt CSP report limiter default to use authenticated Redis.
- Added `docs/00-research/central-runtime-resources.md`.

## Verification

Initial RED:

```powershell
powershell -ExecutionPolicy Bypass -File qa\central-runtime-resources.contract.ps1
```

Failed because `application-kafka.yml` was missing.

GREEN:

```powershell
powershell -ExecutionPolicy Bypass -File qa\central-runtime-resources.contract.ps1
.\gradlew.bat :backend:boot:test --tests com.example.discord.persistence.PersistenceBootstrapTest --rerun-tasks
```

Notes:

- Resource contract printed `CENTRAL_RUNTIME_RESOURCES_CONTRACT_PASS`.
- Postgres/Flyway bootstrap test passed against the configured Postgres profile.

## Six-Metric Review Score

| Metric | Score |
| --- | ---: |
| Plan/Design Alignment | 5/5 |
| TDD Evidence | 5/5 |
| Security/Privacy | 4/5 |
| Integration Compatibility | 4/5 |
| Documentation/Traceability | 5/5 |
| Residual Risk Control | 4/5 |

Total: 27/30

Decision: PASS
