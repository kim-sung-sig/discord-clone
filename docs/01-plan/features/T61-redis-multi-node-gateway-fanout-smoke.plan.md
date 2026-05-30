# T61 Redis Multi-node Gateway Fanout Smoke Plan

Date: 2026-05-21

## Goal

Prove Redis Gateway fanout against a real central Redis instance and prevent consumer-group configuration from turning
broadcast fanout into load-balanced delivery.

## Scope

- Add a Docker-gated central Redis Gateway fanout smoke.
- Prove two logical Gateway nodes both receive the same stream event.
- Prove service-level delivery still filters hidden channel events.
- Keep the smoke integrated with the existing central Redis QA script and CI artifact path.
- Preserve secret-safe Redis checks by continuing to use `REDISCLI_AUTH`.

## Non-goals

- Do not solve full reconnect subscription reconciliation. That remains T62.
- Do not add Redis Testcontainers dependencies while the project already has a central Redis Compose smoke path.
- Do not expose raw stream payloads or secrets in metrics or QA artifacts.

## Acceptance

- RED first: unit test fails while Redis Gateway uses one shared consumer group.
- `RedisGatewayEventBus` uses a node-scoped effective consumer group.
- `qa/central-redis-smoke.ps1` runs the new Gateway fanout smoke.
- The central Redis smoke passes with real Redis and existing web Redis smoke coverage.
