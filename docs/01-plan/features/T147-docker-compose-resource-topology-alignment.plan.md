# T147 Docker Compose Resource Topology Alignment Plan

Date: 2026-05-20
Slice: T147 Docker Compose Resource Topology Alignment

## Objective

Align this repository's Docker Compose topology with the central local resources documented in T144.

## Current State

- Runtime profiles and `.env.example` point to `postgres-source:15432`, `ms-redis:16379`, and `ms-kafka:29092`.
- `infra/docker/docker-compose.yml` still used `postgres:5432`, `redis:6379`, and `redpanda:9092`.

## Scope

1. Extend the central runtime resource contract to cover Docker Compose topology.
2. Rename Compose services to `postgres-source`, `ms-redis`, and `ms-kafka`.
3. Align host port mappings with runtime defaults.
4. Require the shared local Redis password in Compose.
5. Validate Compose syntax.

## Acceptance Criteria

- Central runtime resource contract fails before Compose alignment.
- Contract passes after alignment.
- `docker compose -f infra/docker/docker-compose.yml config` succeeds.
- T147-touched files pass `git diff --check`.
