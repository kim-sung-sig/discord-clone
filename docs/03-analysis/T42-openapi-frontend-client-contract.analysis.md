# T42 OpenAPI & Frontend Client Contract Analysis

작성일: 2026-05-18  
PDCA Phase: Check  
Slice: T42 OpenAPI & Frontend Client Contract

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `node qa/openapi-contract.test.mjs` | RED then PASS | RED failed because `docs/api/openapi.json` did not exist; PASS after deterministic contract artifact generation. |
| `npm test -w packages/api-client` | RED then PASS | RED failed because `packages/api-client/src/index.ts` did not exist; PASS after API client wrapper implementation. |
| `npm run openapi:check` | PASS | `qa/openapi-contract.mjs --check` and contract assertions passed. |
| `pwsh qa/ci-workflow.contract.ps1` | PASS | CI workflow contract includes `npm run openapi:check`. |
| `npm install --package-lock-only` | PASS | New `packages/api-client` workspace is reflected in `package-lock.json`. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Backend OpenAPI spec is generated reproducibly | PASS | `qa/openapi-contract.mjs --write` renders deterministic `docs/api/openapi.json`. |
| Standard error shape includes request id, safe error code, and safe user-facing message | PASS | `ApiErrorResponse` requires `requestId`, `code`, `message`, and `status`. |
| Frontend client contract is generated or validated from the spec | PASS | `packages/api-client/src/generated/openapi-types.ts` is generated from the OpenAPI source. |
| CI fails on uncommitted or incompatible API/client drift | PASS | Frontend CI job now runs `npm run openapi:check`; local contract verifies this snippet. |
| Internal/admin/test endpoints are explicitly included or excluded by policy | PARTIAL | Initial spec includes public representative endpoints and CSP telemetry; full endpoint classification remains follow-up. |
| T42 analysis/report records first drift findings and migration residual risk | PASS | This analysis and T42 report record the implemented slice and gaps. |

## Implementation Notes

- Added `qa/openapi-contract.mjs` with `--write` and `--check` modes.
- Added generated `docs/api/openapi.json`.
- Added generated `packages/api-client/src/generated/openapi-types.ts`.
- Added `@discord-clone/api-client` workspace package.
- Added `createApiClient` wrapper with:
  - `Authorization` header propagation,
  - `X-Request-Id` propagation,
  - `credentials: include`,
  - standard `ApiClientError` normalization from `ApiErrorResponse`.
- Added root scripts:
  - `npm run openapi:write`
  - `npm run openapi:check`
- Added CI frontend job drift gate.

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| OpenAPI spec is currently deterministic source-of-truth, not Spring runtime extraction | Controller annotations/runtime behavior can still drift if developers forget to update the contract | T64 Spring runtime OpenAPI extraction or controller-contract comparison |
| Endpoint coverage is representative, not complete | Some implemented APIs are not yet protected by OpenAPI drift gate | T65 full REST endpoint coverage pass |
| Web app still uses `apps/web/services/discord-api.ts` directly | New `packages/api-client` is available but not migrated into shell/auth stores yet | T66 migrate web REST calls to `@discord-clone/api-client` |
| Error response runtime shape is not globally normalized | OpenAPI defines the target shape; some controllers still return local `ErrorResponse(String message)` | T67 standard backend error envelope |

## Match Rate

Estimated design match: 70%.

The deterministic spec, generated client contract, wrapper package, and CI drift gate are in place. Remaining work is full runtime/controller coverage and migration of existing web calls to the shared package.
