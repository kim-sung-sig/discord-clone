---
name: discord-backend-quality-gate
description: Use when implementing, reviewing, or fixing Java Spring backend code in this Discord repository, especially endpoints, auth, permissions, persistence, gateway, voice, or runtime QA changes
---

# Discord Backend Quality Gate

## Core Rule

Backend changes must be designed around identity, authorization, bounded work, and verifiable gates before code is considered complete.

## Required Context

Read only the minimum context:

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/03-tasking/backend-code-quality-template.md`
- Wiki: `index.md` and `wiki/Backend Architecture.md`
- Add `wiki/QA Infra Operations.md` for tests, profiles, migrations, Kafka, Redis, OpenAPI, or runtime work.

## Implementation Flow

1. Fill the task packet from `docs/03-tasking/backend-code-quality-template.md`.
2. Add or update a failing test for the behavior or security boundary.
3. Implement the smallest fix inside the assigned write paths.
4. Run focused tests.
5. Run `.\gradlew.bat test` and `npm run lint:backend` when the change affects shared backend behavior.
6. Update the external wiki only for durable architecture, QA, or security rules.

## Hard Stops

- Do not trust `memberId`, `ownerId`, or `userId` from request data when bearer identity is available.
- Do not expose guild roles, visible channels, presence, voice, gateway events, or maintenance actions without auth and resource authorization.
- Do not add unbounded event logs, list endpoints, searches, or startup `loadAll()` paths.
- Do not make local PostgreSQL, Redis, Kafka, or LiveKit tests run by default without an opt-in gate.
- Do not log tokens, cookies, passwords, internal publisher tokens, or raw secret-bearing URLs.
- Do not store or compare reusable secrets as raw strings when a hash and constant-time comparison can be used.

## Quick Checks

| Area | Required check |
| --- | --- |
| Auth | `AuthenticatedUserResolver` supplies caller identity. |
| Permission | Channel operations use `canViewChannel` or stronger permission. |
| Cookies | Refresh cookie has `HttpOnly`, `SameSite=Lax`, path, max age, and conditional `Secure`. |
| Rate limit | Auth/signup/refresh/gateway/invite/message hot paths are covered. |
| Events | Replay is bounded and filtered by viewer access. |
| Secrets | Revoked token state stores hashes; internal/webhook token checks use constant-time comparison. |
| Persistence | Query-shaped, bounded reads are preferred over snapshot load/save. |
| Tests | RED/GREEN evidence plus focused and regression commands are recorded. |

## Return Format

```text
status: DONE | DONE_WITH_CONCERNS | BLOCKED
changed files:
RED evidence:
GREEN evidence:
commands:
wiki used/updated:
residual risks:
```
