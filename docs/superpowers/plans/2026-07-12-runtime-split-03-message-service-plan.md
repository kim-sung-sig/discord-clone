# Runtime Split: Message and Room Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make message-service the sole owner of rooms/channels, membership, room-owner checks, message persistence, and message publication events.

**Architecture:** Room membership is the authorization source for message reads and writes. The service retains the current idempotency and transactional-outbox design, then emits a versioned event for websocket-service. It does not call the websocket-service synchronously.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, JDBC, Kafka/Redis event bus, JUnit 5.

---

## File Structure

- Move from: `backend/boot/src/main/java/com/example/discord/message/**`.
- Move from: `backend/boot/src/main/java/com/example/discord/guild/**`, `channel/**`, `invite/**` only where required for room ownership/membership.
- Reuse: `backend/modules/message/**`, `backend/modules/channel/**`, `backend/modules/guild/**`, `backend/modules/invite/**` as framework-light domain dependencies.
- Create: `backend/services/message/src/main/java/com/example/discord/{message,room}/**`.
- Create: message-owned migrations and service-specific PostgreSQL configuration.

### Task 1: Move room authorization first — 1 MM

- [ ] Define one message-service room access port with operations `canRead`, `canSend`, and `isOwner` for an authenticated subject and room/channel target.
- [ ] Write failing tests proving a non-member cannot list or send, a member can send, and only a room owner can perform owner-only mutations.
- [ ] Move only the needed guild/channel membership adapters; defer generic role/permission mutation APIs.
- [ ] Derive the actor solely from the verified JWT subject; reject request-provided owner/member identifiers.

### Task 2: Move message API and persistence — 1.5–2 MM

- [ ] Move `MessageController`, `MessageConfiguration`, JDBC store, read model, idempotency state, and publication outbox to message-service.
- [ ] Preserve client-generated idempotency keys and the same-key/different-payload `409` behavior.
- [ ] Write RED/GREEN controller and domain tests for retry, membership rejection, cursor-bounded read, edit/delete authorization, and message-body-safe logging.
- [ ] Keep PostgreSQL adapter tests opt-in through `DISCORD_RUN_POSTGRES_TESTS=true`.

### Task 3: Publish a websocket event contract — 1 MM

- [ ] Define a versioned `MessagePublished` payload containing safe IDs, event type, sequence/idempotency information, and delivery target; do not publish raw credentials or internal store details.
- [ ] Keep outbox claim/retry/dead-letter behavior in message-service.
- [ ] Add a broker contract test that a successful committed message produces one event and a failed relay remains retryable.

### Task 4: Cut over routes — 0.5–1 MM

- [ ] Route message and room paths to message-service.
- [ ] Run authenticated create → outbox relay → broker event smoke through the Gateway.
- [ ] Remove duplicate message/room runtime beans only after the service owns the route and data path.

**Exit gate:** message-service is the only writer for room/message state and emits delivery events asynchronously.

