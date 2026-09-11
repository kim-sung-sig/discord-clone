---
slug: T153-compose-health-gate-script
ticket: T153
phase: analysis
hub: "[[T153-compose-health-gate-script]]"
---
> 🧭 [[T153-compose-health-gate-script]] · PDCA: [[T153-compose-health-gate-script.plan]] → [[T153-compose-health-gate-script.design]] → **analysis** → [[T153-compose-health-gate-script.report]] → [[T153-compose-health-gate-script.feedback]]

# T153 Compose Health Gate Script Analysis

Date: 2026-05-20
Slice: T153 Compose Health Gate Script

## Findings

- The developer environment already has standalone central containers named `postgres-source`, `ms-redis`, and `ms-kafka`.
- Compose service names match those central names, but Compose-created container names still include the project prefix.
- Health checks must support both standalone central containers and Compose-managed service containers.

## Tradeoffs

- Kafka readiness uses a TCP check because the running Redpanda container does not expose `rpk` in `PATH`.
- Redis readiness emits the standard `redis-cli -a` warning; the command still returns a reliable `PONG`.

## Follow-Up

- Add clearer diagnostics for port conflicts and unhealthy containers if a central resource fails to become ready.
