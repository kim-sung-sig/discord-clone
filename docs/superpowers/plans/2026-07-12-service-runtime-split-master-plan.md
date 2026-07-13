# Service Runtime Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `backend:boot` runtime with independently deployable identity, message, websocket, and community services; keep the Gateway as routing/load-balancing configuration only.

**Architecture:** The Gateway owns only TLS termination, path routing, base URLs, and load balancing. `identity-service`, `message-service`, and `community-service` each own their data; `websocket-service` owns authenticated connections and event delivery but no business database. Services exchange external events through the existing broker contract; no service calls another service's database.

**Tech Stack:** Java 21, Spring Boot 3.3, Gradle, PostgreSQL, Redis/Kafka or Redpanda, Docker Compose, Grafana Alloy/Loki, JUnit 5.

**Approval:** Approved — Plan 00 ownership map was accepted on 2026-07-13. Do not begin a later implementation plan until its preceding plan has passed its exit gate.

---

## Fixed boundaries

| Runtime | Owns | Does not own |
| --- | --- | --- |
| Gateway / load balancer | routing, base URLs, TLS, balancing | JWT validation, WebSocket sessions, domain code, DB |
| identity-service | accounts, credentials, refresh tokens, social identity, MFA, email, profile | rooms, messages, sockets |
| message-service | rooms/channels, membership, owner checks, messages, message events | account credentials, sockets |
| websocket-service | socket lifecycle, connect-time JWT validation, subscriptions, fan-out | REST domain APIs, domain DB, room/message writes |
| community-service | posts/threads/comments and their events | accounts, socket sessions, room/message writes |

**Explicitly excluded:** administrator console, global RBAC/ABAC, generic role/permission management, new media/bot/moderation features, and a Java “gateway service.”

## Execution order and estimate

| Order | Plan | Exit condition | Estimate |
| ---: | --- | --- | ---: |
| 0 | [00 boundary inventory](2026-07-12-runtime-split-00-boundary-inventory-plan.md) | ownership map and freeze list accepted | 1 MM |
| 1 | [01 runtime skeleton](2026-07-12-runtime-split-01-runtime-skeleton-plan.md) | four services start; routing and health checks work | 3 MM |
| 2 | [02 identity](2026-07-12-runtime-split-02-identity-service-plan.md) | identity owns all account/JWT paths | 3–4 MM |
| 3 | [03 message](2026-07-12-runtime-split-03-message-service-plan.md) | room/message API and events are isolated | 4–5 MM |
| 4 | [04 websocket](2026-07-12-runtime-split-04-websocket-service-plan.md) | authenticated fan-out has no domain DB dependency | 3–4 MM |
| 5 | [05 community](2026-07-12-runtime-split-05-community-service-plan.md) | community API/events are isolated | 2–3 MM |
| 6 | [06 cutover](2026-07-12-runtime-split-06-cutover-and-ha-plan.md) | old runtime is removed or read-only; HA checks pass | 2–3 MM |

**Total:** 18–23 MM for the scoped core. Add 2 MM for production deployment automation if no target platform manifests already exist. With three backend engineers, one platform engineer, and QA this is 8–10 calendar weeks; one developer should not schedule it below 5 months.

## Non-negotiable invariants

- Gateway must contain no Java application, JWT logic, event listener, or database connection.
- A service reads only its own schema/database; foreign data arrives through a versioned REST/event contract.
- WebSocket authentication validates a JWT issued by identity-service; it does not query identity-service per message.
- Message authorization uses room membership/ownership held by message-service. Global RBAC/ABAC is not introduced.
- Every service propagates `X-Request-Id` and W3C trace context; logs exclude passwords, tokens, cookies, and message bodies.
- A migration step is reversible through gateway routing until its contract and smoke checks pass.

## Shared verification gates

```bash
./gradlew :backend:boot:test
npm run openapi:check
docker compose -f infra/docker/docker-compose.yml config
git diff --check
```

Runtime and broker tests remain opt-in so ordinary unit tests do not require PostgreSQL, Redis, or Kafka.
