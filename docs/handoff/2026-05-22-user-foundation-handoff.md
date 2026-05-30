# User Foundation Handoff

Created: 2026-05-22 09:39:52 +09:00
Repository: `C:\git\discord`
Current branch: `feature/t02-guild-channel-permission`
Latest committed work reviewed: `391a715 feat(web): add csp security dashboard incident history`

## Current Goal

Build the Discord clone toward a production-quality user-facing product first.
Prioritize user workflows, scalable message/gateway foundations, security boundaries, and testable frontend quality.

The operations dashboard is no longer a primary product priority. Operational observability should be handled by external systems such as Grafana, Prometheus, Loki, ELK/OpenSearch, or equivalent runtime dashboards. Keep only security-relevant in-app surfaces when they directly protect the application or expose required security posture.

## User Direction

- User-facing product foundation comes before admin/operator dashboards.
- High traffic will originate mostly from user workflows: login/session restore, guild/channel navigation, message send/read/search, gateway event delivery, presence, voice state, invite flows, and moderation safety.
- Security remains important, but security work should support the user product and runtime safety rather than becoming a broad custom operations dashboard.
- Approval-required steps should not be polled repeatedly. Stop and wait for the user to resume. Gmail notification would be useful if available in a future session.
- Use subagents more actively, up to 10 total when work can be split safely.
- Project-local subagent personas are now defined under `.codex/agents/`.
- Future screens should be JSON-driven where practical and support localization and theme selection.

## Current State Summary

### Recently Completed

- T167 security dashboard incident history was committed as `391a715`.
- Browser screenshots were captured:
  - `C:\git\discord\progress-user-shell.png`
  - `C:\git\discord\progress-security-dashboard.png`
- Project-local agent persona files were added:
  - `C:\git\discord\.codex\agents\README.md`
  - `C:\git\discord\.codex\agents\EXPLORER.md`
  - `C:\git\discord\.codex\agents\DEVELOPER.md`
  - `C:\git\discord\.codex\agents\QA-REVIEWER.md`

### Important Dirty Worktree Note

The worktree contains many unrelated modified and untracked files from prior work. Do not revert them. Stage and commit only task-owned paths.

Use:

```powershell
git -c safe.directory=C:/git/discord status --short
```

## Architecture Review Findings

### High-Traffic User Path Is Not Production-Ready Yet

The current message path is synchronous and memory-heavy:

- `POST /api/channels/{channelId}/messages` calls the message service synchronously in `backend/boot/src/main/java/com/example/discord/message/MessageController.java`.
- `PersistentMessageService` is a JVM-local `synchronized` wrapper around `InMemoryMessageService`, then performs one JDBC snapshot save per mutation:
  - `backend/boot/src/main/java/com/example/discord/message/PersistentMessageService.java`
- Message reads/search still stream/filter/sort in memory:
  - `backend/modules/message/src/main/java/com/example/discord/message/InMemoryMessageService.java`
- Startup loads all messages and related rows:
  - `backend/boot/src/main/java/com/example/discord/message/JdbcMessageSnapshotStore.java`

Treat current message persistence as a prototype bridge, not the scalable final path.

### Virtual Threads And Async Pools

No backend evidence was found for:

- `spring.threads.virtual.enabled`
- virtual thread executor
- `@Async`
- `@EnableAsync`
- dedicated `TaskExecutor`

The backend is Java 21 + Spring MVC, but virtual threads are not currently enabled or verified.

### Transactions, SAGA, Outbox, Black-Box Transaction Pattern

No backend evidence was found for:

- Spring `@Transactional`
- `TransactionTemplate`
- SAGA/process manager implementation
- transactional outbox
- after-commit event publisher
- black-box transaction/idempotency table

Existing JDBC stores manually call `setAutoCommit(false)`, `commit`, and `rollback` locally. That protects a narrow store operation but does not make domain mutation + persistence + event publication atomic.

### Event-Driven Runtime

Gateway fanout foundations exist:

- Redis Streams gateway bus:
  - `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java`
  - Poll delay defaults to 250ms and reads up to 100 records per poll.
  - Redis stream max length defaults to 10,000.
  - DLQ and metrics counters exist.
- Kafka gateway bus:
  - `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java`
  - Topic/DLQ support exists.
- Redis session registry:
  - `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewaySessionRegistry.java`
  - Session TTL defaults to 86,400 seconds.

However, this is mostly gateway fanout plumbing. It is not yet a durable domain-event architecture tied to database commits.

### Message Sequencing Risk

`JdbcMessageSnapshotStore` computes per-channel sequence with `MAX(sequence) + 1`.
This can race across nodes. The schema has `UNIQUE(channel_id, sequence)`, but no row lock, DB sequence allocator, retry loop, or idempotent command table was found.

### Connection Pooling / Backpressure

Postgres persistence uses raw datasource wiring. No clear Hikari sizing, queueing, request backpressure, Kafka listener concurrency, or producer ack policy was evidenced.

## Frontend / UX Review Findings

### User Shell Exists, But Still Reads As Demo-Oriented

The user shell includes guild/channel/message/DM/forum/member/role/gateway/invite/moderation/voice panels in:

- `apps/web/pages/app.vue`
- `apps/web/stores/shell.ts`
- `apps/web/services/discord-api.ts`

But many surfaces are seeded/demo state, and the app shell currently renders broadly without a strong auth/product boundary.

### Auth Boundary Gap

Global auth middleware restores session but does not gate or redirect:

- `apps/web/middleware/auth.global.ts`

Most shell surfaces render regardless of authenticated state. Only the backend smoke action is token-gated.

### JSON-Driven UI, i18n, Theme

Current state is not ready:

- User-facing copy is hard-coded across Vue components and Pinia stores.
- `apps/web/package.json` does not show an i18n dependency.
- Shell content is embedded in `apps/web/stores/shell.ts`, not loaded from JSON/contract fixtures.
- Theme work exists, but future screen definitions should separate theme tokens from workflow logic.

### Visual Quality Gaps

Existing screenshot evidence shows text-fit issues in some visual smoke artifacts, including awkward label breaks.
The current browser capture also produced console issues:

- CSP blocks Vite dev-client inline styles.
- Vue hydration mismatch warnings.
- Backend refresh call fails when `127.0.0.1:8080` is not running.

Console log captured at:

- `C:\git\discord\progress-console.log`

## Recommended Next Work

### P0: Reframe Product Priority

Stop expanding the custom operations dashboard.
Keep security dashboard work only where it supports security posture, incident visibility, or access control.
Move general ops metrics to external observability:

- Prometheus/Grafana for metrics
- Loki/ELK/OpenSearch for logs
- Tempo/Jaeger for traces if tracing is added

### P1: User Foundation Slice

Implement a real user-facing happy path:

1. Authenticated app shell boundary.
2. Real guild/channel list loaded from backend.
3. Real message list backed by database pagination.
4. Message send persists first, then publishes a durable event.
5. Gateway receives message-created event and updates connected clients.
6. UI copy comes from locale JSON.
7. Theme tokens are selected from a structured theme config.

### P1: Message Persistence Redesign

Replace the current synchronized memory-first message path with:

- DB-first write model.
- Per-channel sequence allocation that is safe across nodes.
- Paginated DB query for reads.
- Search backed by DB indexes initially, then external search later if needed.
- Idempotent command/request key for retries.
- Transactional outbox for message-created/message-updated/message-deleted events.

### P1: Event-Driven Consistency

Add a transactional outbox before claiming SAGA/event-driven reliability:

1. Write message and outbox row in one DB transaction.
2. Outbox dispatcher publishes to Redis/Kafka.
3. Mark outbox row delivered with retry/DLQ.
4. Gateway consumers deduplicate by event id.

Use SAGA/process manager only for multi-step workflows that actually need compensation, such as invite acceptance with membership side effects, billing-like flows, or cross-resource moderation workflows.

### P1: Runtime Scalability Evidence

Do not claim traffic capacity until measured.
Add focused load tests for:

- message send TPS
- message list pagination latency
- gateway connected session count
- fanout events/sec
- Redis/Kafka DLQ behavior
- auth/session restore under load

### P2: Frontend Configurability

Introduce:

- `locales/ko-KR.json`
- `locales/en-US.json`
- `themes/default.json`
- typed UI config schema for shell labels/panels
- tests that assert copy is not hard-coded in new configurable screens

## Suggested Subagent Plan

Use up to 10 agents, but keep write agents serialized by shared files.

### Explorer Agents

- EXPLORER 1: message persistence and schema redesign options.
- EXPLORER 2: gateway fanout and outbox integration points.
- EXPLORER 3: frontend auth boundary and shell data-loading gaps.
- EXPLORER 4: i18n/theme/json-driven UI inventory.
- EXPLORER 5: load-test and verification gate design.

### Developer Agents

Run developers only with disjoint write paths.

- DEVELOPER A: backend message repository/outbox tests and implementation.
- DEVELOPER B: frontend i18n/theme config scaffolding.
- DEVELOPER C: auth-gated user shell route behavior.

Avoid parallel edits to:

- `apps/web/stores/shell.ts`
- generated OpenAPI files
- database migrations
- auth/permission paths
- shared gateway interfaces

### QA Reviewers

For each developer task:

1. QA-REVIEWER spec compliance pass.
2. QA-REVIEWER code quality/security/concurrency pass.
3. Runtime observer pass for focused tests and screenshots.

P0/P1 findings block commit.

## Required Workflow For Next Agent

Before editing:

1. Read `AGENTS.md`.
2. Read `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md`.
3. For backend work, read:
   - `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Backend Architecture.md`
   - `backend/AGENTS.md`
4. For frontend work, read:
   - `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Frontend Client Architecture.md`
5. For QA/runtime, read:
   - `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\QA Infra Operations.md`
6. Use `.codex/agents/` role files when dispatching subagents.
7. Follow TDD for behavior changes.

## Useful Evidence Files

- User shell screenshot: `C:\git\discord\progress-user-shell.png`
- Security dashboard screenshot: `C:\git\discord\progress-security-dashboard.png`
- Console log: `C:\git\discord\progress-console.log`
- Subagent role packets: `C:\git\discord\docs\03-tasking\subagent-role-packets.md`
- Project-local agent personas: `C:\git\discord\.codex\agents\`

## Handoff Decision

The next agent should not continue building custom operator dashboard features.
The next agent should start with a user-facing foundation task, preferably:

> Implement the authenticated, database-backed user message workflow with DB pagination and a transactional outbox skeleton, then connect the frontend shell to that real path with localized/theme-aware labels.

This should be broken into smaller TDD tasks before implementation.
