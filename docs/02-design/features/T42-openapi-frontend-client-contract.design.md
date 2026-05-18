# T42 OpenAPI & Frontend Client Contract Design

작성일: 2026-05-17  
PDCA Phase: Design  
Slice: T42 OpenAPI & Frontend Client Contract

## Architecture Decision

Use OpenAPI as the backend/frontend REST contract boundary and keep generated artifacts deterministic. Backend owns endpoint behavior and schema; frontend consumes typed request/response contracts through a generated or validated client layer under `packages/api-client`.

The contract should serve web, PWA, desktop, and any future native shell without duplicating API types per platform.

## Contract Boundaries

| Boundary | Owner | Purpose |
| --- | --- | --- |
| Backend OpenAPI spec | Spring Boot app | Canonical REST request/response shape |
| Standard error schema | Backend ops/security layer | Shared safe error contract |
| `packages/api-client` | Frontend platform family | Typed API client and error normalization |
| CI drift gate | QA workflow | Detect spec/client mismatch before merge |

## Endpoint Classification

Classify endpoints into three groups:

- Public client API: auth, user, guild, channel, message, invite, voice, stage, presence, notifications when added.
- Operator/admin API: moderation, audit search, security reports, admin console endpoints.
- Internal/test API: health, test harness, local fixture endpoints.

Rules:

- Public and operator APIs must be documented.
- Internal/test APIs must be excluded from generated production client or clearly tagged.
- Security-sensitive endpoints must document auth, permission, rate limit, and error shapes.

## Error Shape

Standard error response:

```json
{
  "requestId": "req-...",
  "code": "MESSAGE_FORBIDDEN",
  "message": "You cannot send messages in this channel.",
  "status": 403
}
```

Rules:

- `requestId` is always present.
- `code` is stable and safe for UI branching.
- `message` is safe for user display and must not include secrets or raw exception text.
- Validation errors can include field-level safe details.

## Client Generation Strategy

Preferred path:

1. Generate `openapi.json` from backend.
2. Generate TypeScript types/client into `packages/api-client/src/generated`.
3. Wrap generated calls with project-specific auth, request id, retry/no-retry, and error normalization helpers.
4. Keep handwritten UI code importing only from stable wrapper modules.

This avoids coupling Vue components to generator-specific output.

## Drift Gate

CI should run:

```text
generate backend OpenAPI spec
generate frontend API client
git diff --exit-code on generated contract artifacts
run api-client tests
```

If generated files differ, the gate fails and requires committing the contract update or reverting the backend change.

## Versioning Policy

- Additive fields are compatible if frontend ignores unknown fields.
- Removed/renamed fields require migration note and frontend update in the same change.
- Error code changes are breaking unless aliases are kept.
- Endpoint path/method changes are breaking unless old endpoint remains during migration.
- Auth/permission changes must be reflected in endpoint docs and tests.

## QA Strategy

- Backend test verifies OpenAPI generation succeeds.
- Contract test verifies standard error schema is present.
- API client test verifies request id and auth header propagation through wrappers.
- Drift test verifies generated artifacts are committed.
- Representative real-backend smoke should continue using the generated client where feasible.

## Risks

- Generated clients can be awkward for hand-written UI; keep wrappers stable and generator output isolated.
- OpenAPI annotations can drift from runtime behavior; pair spec generation with controller/contract tests.
- Large initial generation can create noisy diffs; stage endpoint coverage if needed.
- Internal endpoints must not be accidentally packaged into public client surfaces.
