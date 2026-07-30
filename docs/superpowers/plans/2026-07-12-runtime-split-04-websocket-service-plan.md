# Runtime Split: WebSocket Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Isolate authenticated WebSocket connection handling and event fan-out in websocket-service with no domain database.

**Architecture:** websocket-service validates an identity-issued JWT at connection time, maintains ephemeral session/subscription state, consumes authorized delivery events, and writes frames to connected clients. It neither owns room/message data nor accepts REST writes that create domain events. Gateway only upgrades/routes `/ws`.

**Tech Stack:** Java 21, Spring Boot WebSocket, JWT verifier, Redis/Kafka, JUnit 5.

---

## File Structure

- Move from: `backend/boot/src/main/java/com/example/discord/gateway/**`.
- Reuse: `backend/modules/gateway/**` as realtime domain/service contracts.
- Create: `backend/services/websocket/src/main/java/com/example/discord/websocket/**`.
- Create: websocket service resource configuration with broker and JWT verifier settings only.
- Modify: `infra/gateway/` route configuration for WebSocket upgrade and `/ws` upstream.

### Task 1: Move socket lifecycle — 1 MM

- [ ] Move `GatewayWebSocketConfiguration`, handler, heartbeat/resume/session maintenance, and tests into websocket-service.
- [ ] Rename package/runtime terminology from `gateway` to `websocket` where it clarifies the external role; do not rename shared domain types merely for cosmetics.
- [ ] Add RED/GREEN tests for identify with a valid token, expired/malformed token rejection, heartbeat, close cleanup, and bounded replay.

### Task 2: Remove domain database dependencies — 0.75–1 MM

- [ ] Assert the websocket application's context starts with no `DataSource`, Flyway, JDBC store, or message/room repository bean.
- [ ] Keep only ephemeral local session state plus Redis/broker adapters required for multi-node delivery.
- [ ] Add a test that an unavailable domain database cannot prevent WebSocket service startup.

### Task 3: Consume and fan out delivery events — 1 MM

- [ ] Subscribe to the message-service event contract and dispatch only to matching authorized session targets.
- [ ] Reject forged public HTTP event-publish endpoints; broker/internal publisher boundaries remain internal.
- [ ] Test one delivery across two websocket nodes and ensure source-node duplicate suppression remains intact.

### Task 4: Cut over `/ws` — 0.5–1 MM

- [ ] Route WebSocket upgrade traffic to websocket-service through the Gateway.
- [ ] Run connect with JWT → publish message through message-service → receive one frame smoke.
- [ ] Remove WebSocket handler/configuration from the old runtime after the smoke passes.

**Exit gate:** websocket-service has no domain DB and can horizontally scale while delivering one authorized event per subscribed socket.

