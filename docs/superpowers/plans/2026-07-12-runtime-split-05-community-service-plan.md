# Runtime Split: Community Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Isolate forum/thread and future post/comment capability behind community-service without coupling it to message or websocket databases.

**Architecture:** Existing thread/forum behavior forms the first community slice. Identity is verified locally from JWT; any cross-service references use stable user/room IDs and events/contracts, not foreign table reads. Post/comment features are added only after this runtime boundary is proven.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, JDBC, Kafka/Redis event bus, JUnit 5.

---

## File Structure

- Move from: `backend/boot/src/main/java/com/example/discord/thread/**`.
- Reuse: `backend/modules/thread/**` as the framework-light domain module.
- Create: `backend/services/community/src/main/java/com/example/discord/community/**`.
- Create: community-owned migrations and configuration.
- Modify: `infra/gateway/` community path routing.

### Task 1: Move thread/forum runtime — 1 MM

- [ ] Move `ThreadController`, `ThreadConfiguration`, `JdbcThreadService`, and their tests into community-service.
- [ ] Write RED/GREEN tests for authenticated create/read, archive/reopen, archived-write rejection, expiration, and bounded page reads.
- [ ] Keep the community schema separate from message and identity schemas.

### Task 2: Define cross-service references — 0.5 MM

- [ ] Define user/room references as immutable IDs in community records; do not introduce foreign keys across services.
- [ ] Use identity JWT subject for actor identity and a contract/event lookup for any message/room relationship needed by future features.
- [ ] Publish only a minimal `CommunityPostPublished`-style event if a client delivery use case exists; otherwise do not add an event yet.

### Task 3: Cut over and defer expansion — 0.5–1 MM

- [ ] Route `/community` paths to community-service.
- [ ] Run authenticated create → list → archive/reopen smoke through the Gateway.
- [ ] Record posts/comments as a separate follow-on plan; do not combine them with runtime extraction.

**Exit gate:** existing community/thread behavior runs independently and does not read another service database.

