# T153 Compose Health Gate Script Plan

Date: 2026-05-20
Slice: T153 Compose Health Gate Script

## Objective

Add one repeatable readiness gate for the central local Postgres, Redis, and Kafka resources.

## Current State

- Individual smokes can start or reuse central Redis and Kafka.
- Docker Compose defines `postgres-source`, `ms-redis`, and `ms-kafka`.
- There was no single script to verify all central resources are ready before running integration smokes.

## Scope

1. Add a contract for the central compose health gate.
2. Add a QA script for Postgres, Redis, and Kafka readiness.
3. Reuse existing standalone central containers when they are already healthy.
4. Start missing Compose services and wait for readiness.

## Acceptance Criteria

- Contract fails before the health script exists and passes after implementation.
- Health script verifies `postgres-source` with `pg_isready`.
- Health script verifies `ms-redis` with `redis-cli ping`.
- Health script verifies `ms-kafka` broker port `29092`.
- Health script emits `CENTRAL_COMPOSE_HEALTH_PASS`.
- T153-touched files pass `git diff --check`.
