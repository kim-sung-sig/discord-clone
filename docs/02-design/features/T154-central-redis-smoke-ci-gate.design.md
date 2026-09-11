---
slug: T154-central-redis-smoke-ci-gate
ticket: T154
phase: design
hub: "[[T154-central-redis-smoke-ci-gate]]"
---
> 🧭 [[T154-central-redis-smoke-ci-gate]] · PDCA: [[T154-central-redis-smoke-ci-gate.plan]] → **design** → [[T154-central-redis-smoke-ci-gate.analysis]] → [[T154-central-redis-smoke-ci-gate.report]] → [[T154-central-redis-smoke-ci-gate.feedback]]

# T154 Central Redis Smoke CI Gate Design

Date: 2026-05-20
Slice: T154 Central Redis Smoke CI Gate

## Design

The CI gate is a dedicated `qa-central-redis` job in `.github/workflows/ci.yml`.

## Job Steps

1. Checkout repository.
2. Set up Java 21 with Gradle cache.
3. Set up Node 22 with npm cache.
4. Run `npm ci`.
5. Make `./gradlew` executable.
6. Run central compose and Redis smoke contracts.
7. Run `pwsh qa/central-redis-smoke.ps1` with `SPRING_DATA_REDIS_PASSWORD=dev_password`.

## Script Portability

`qa/central-redis-smoke.ps1` now resolves the Gradle wrapper at runtime:

- Windows: `gradlew.bat`
- Non-Windows: `gradlew`

Native commands are invoked through a helper that throws on non-zero exit codes so CI failures are not masked.
