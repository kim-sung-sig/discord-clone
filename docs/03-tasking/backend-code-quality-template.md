# Backend Code Quality Template

Purpose: use this template before adding or changing backend code so security, authorization, scalability, and verification are designed into the slice instead of reviewed in afterward.

## Task Packet

```text
Task ID:
Goal:
Domain:
Allowed write paths:
Forbidden changes:
Affected endpoints:
Affected domain services:
Persistence/runtime profiles:
External dependencies:
Expected tests:
Expected docs/wiki updates:
```

## Endpoint Template

Every controller endpoint must answer these before implementation:

| Question | Required answer |
| --- | --- |
| Who is the caller? | Derive user identity from `AuthenticatedUserResolver`, not request body/query/path. |
| Is the endpoint public? | If yes, document why. Default is bearer auth. |
| What resource is protected? | Guild, channel, message, session, voice room, attachment, admin resource, or internal event. |
| What authorization is required? | Membership, `canViewChannel`, `canSendMessages`, `canManage*`, global role, or internal service token. |
| Can the caller impersonate another user? | No, unless endpoint is an audited admin tool. |
| Is request data validated? | Use `@Valid @RequestBody` and field constraints for HTTP shape; keep business invariants in domain services. |
| Is output bounded? | Add `limit`/cursor or a retained-event window. |
| Is rate limiting needed? | Required for auth, signup, refresh, gateway identify, invite accept, and message creation. |
| Does it emit gateway/voice/events? | Emit only after successful domain mutation and filter delivery by channel visibility. |

## Security Defaults

- Refresh cookies use `HttpOnly`, `Path=/api/auth`, bounded `Max-Age`, `SameSite=Lax`, and conditional `Secure`.
- `X-Forwarded-For` is trusted only when the immediate peer is a trusted proxy.
- Production must reject known development secrets, including Redis `dev_password`.
- Internal publisher endpoints require an internal token/profile boundary; user bearer auth alone is not enough.
- Never log raw access tokens, refresh tokens, cookies, passwords, or internal publisher tokens.
- Store token revocation records and webhook/internal token comparators as hashes, and compare shared secrets with constant-time comparison.

## Scalability Defaults

- Do not add new unbounded lists, global event logs, or startup `loadAll()` paths.
- In-memory services used for tests/local runtime must still have bounded work for hot paths.
- Persistent adapters should use query-shaped repositories and cursor/limit reads instead of whole-system snapshots.
- Blocking database work must not sit inside broad synchronized service locks.
- Gateway and voice event replay must have retention and maximum delivery bounds.
- Moderation, audit, alert, report, and webhook audit logs must have per-resource retention bounds and bounded read limits.

## Test Template

Each backend change should include the smallest RED/GREEN set:

```text
RED:
- Auth missing test:
- Authorization denied test:
- Positive authorized test:
- Validation/error-shape test:
- Bound/rate/retention test:

GREEN command:
- .\gradlew.bat :backend:boot:test --tests <ControllerOrAdapterTest>
- .\gradlew.bat :backend:modules:<module>:test --tests <DomainTest>

Regression command:
- .\gradlew.bat test
- npm run lint:backend
```

Environment-backed tests must be opt-in by environment variable unless the repository provides the dependency automatically.

## Review Checklist

- No controller trusts `memberId`, `ownerId`, or `userId` from request data when token identity is available.
- Read endpoints exposing guild topology require bearer auth and membership or stronger permission.
- Channel-scoped reads/writes call a channel visibility or permission check.
- Event polling, websocket replay, voice events, and moderation/audit reads are bounded.
- Controller request DTOs are validated consistently.
- Domain modules do not import Spring/Jakarta/web/persistence APIs.
- Persistent tests do not fail on a workstation without PostgreSQL/Redis/Kafka unless explicitly opted in.
- `npm run lint:backend` and relevant Gradle tests pass.
