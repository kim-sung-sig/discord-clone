# Runtime Split: Boundary Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze unrelated expansion and produce the source-of-truth ownership map used by all later extraction plans.

**Architecture:** This is documentation and contract work only. It assigns existing `backend:boot` controllers/configuration to one future runtime or explicitly defers them; it creates no new service and changes no behavior.

**Tech Stack:** Markdown, Gradle module map, Spring Boot source inventory.

---

## File Structure

- Create: `docs/03-tasking/runtime-split-ownership-map.md` — controller/configuration ownership, data owner, and deferred list.
- Modify: `docs/03-tasking/improvement-task-backlog.md` — mark superseded admin/permission/runtime expansion work as deferred.
- Modify: `docs/superpowers/plans/2026-07-12-service-runtime-split-master-plan.md` — replace Draft with Approved only after user approval.

### Task 1: Create the ownership map — 0.5 MM

- [ ] List every controller and `*Configuration` under `backend/boot/src/main/java/com/example/discord`.
- [ ] Assign `AuthController`, `AuthConfiguration`, `AuthenticatedUserResolver`, `AuthStore`, user/profile and social account paths to identity-service.
- [ ] Assign `MessageController`, `MessageOutboxController`, `MessageConfiguration`, guild/channel/invite/membership paths to message-service.
- [ ] Assign `GatewayController`, `GatewayWebSocketConfiguration`, `GatewayWebSocketHandler`, session registry, and event-bus adapters to websocket-service; the name `gateway` in existing Java packages means realtime delivery, not the load balancer.
- [ ] Assign thread/forum/post paths to community-service; leave unimplemented post/comment capability out of scope.
- [ ] Mark experience, expression, presence, storage, voice, moderation, bot, notification, admin CLI, and global permission management as deferred. Do not silently assign them.

### Task 2: Freeze scope — 0.25 MM

- [ ] Add a backlog note: no new admin, generic RBAC/ABAC, global role, dashboard, Kafka hardening, or media feature task starts until Plan 01 completes.
- [ ] Record any currently active changes separately; do not modify or discard unrelated working-tree changes.

### Task 3: Review the map — 0.25 MM

- [ ] Run `rg -n '@RestController|class .*Configuration' backend/boot/src/main/java` and reconcile every hit with the map.
- [ ] Run `git diff --check`.
- [ ] Commit only the three plan/ownership files: `git commit -m "docs: define service runtime ownership"`.

**Exit gate:** the user accepts the ownership map; no ambiguity remains about the four runtime boundaries.

