# Runtime Split: Cutover and High-Availability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely retire the monolithic runtime path and prove the four-service system handles node loss, routing changes, and observable request traces.

**Architecture:** Gateway routing is the rollback lever. Each service has independent health checks and replicas; stateful data remains in service-owned Postgres/Redis/broker infrastructure. The old `backend:boot` runtime is removed only after routes, data ownership, and rollback smoke pass.

**Tech Stack:** Docker Compose for local verification, deployment platform manifests, PostgreSQL, Redis/Kafka, Alloy/Loki/trace propagation, Gradle/JUnit and runtime smoke scripts.

---

## File Structure

- Modify: `infra/docker/docker-compose.yml` — final service topology and replica-capable dependency configuration.
- Modify: `infra/gateway/` — final upstreams, health probes, timeouts, and rollback routing.
- Create: `qa/runtime-split-smoke.*` — narrow service routing/failure smoke through the existing QA harness style.
- Modify/Delete: `backend/boot/**` — remove only runtime code that has a verified owner.
- Modify: external wiki architecture/roadmap only after verification accepts durable changes.

### Task 1: Build the cutover matrix — 0.5 MM

- [ ] For each public path, record old upstream, new upstream, rollback switch, data owner, and smoke command.
- [ ] Schedule one route family at a time: auth, message, websocket, then community.
- [ ] Do not route a public path to two writable owners at once.

### Task 2: Run service contract and failure checks — 1 MM

- [ ] Verify Gateway health-based removal of an unhealthy identity/message/community instance.
- [ ] Verify websocket reconnects to a healthy node and duplicate delivery does not occur after a node failure.
- [ ] Verify a request trace can connect Gateway, the chosen service, and its event delivery without token/message-body leakage.
- [ ] Load-test only defined hot paths: login/refresh, message publish, and WebSocket fan-out. Record target concurrency, latency percentile, error rate, and hardware/runtime assumptions before declaring capacity.

### Task 3: Remove the old runtime — 0.5–1 MM

- [ ] Search each `backend:boot` class before deletion and remove it only after all callers route to its new owner.
- [ ] Delete duplicate beans/controllers/configuration in small commits by service.
- [ ] Keep a tagged or deployable prior release for rollback; do not preserve a second writable code path.

### Task 4: Final verification and knowledge update — 0.5 MM

- [ ] Run focused service tests, `./gradlew test`, `npm run openapi:check`, Compose config validation, and runtime split smoke.
- [ ] Review implementation against the master-plan invariants: Gateway-only routing, data ownership, local JWT verification, no websocket DB, trace/log safety.
- [ ] Update `Backend Architecture.md`, `QA Infra Operations.md`, `Current Roadmap And Risks.md`, and wiki `log.md` only after the review passes.

**Exit gate:** all production routes have one owner, old runtime behavior is gone, rollback is proven, and HA claims are backed by recorded failure/load results.

