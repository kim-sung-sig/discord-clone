# T42 OpenAPI & Frontend Client Contract Report

작성일: 2026-05-18  
PDCA Phase: Report  
Slice: T42 OpenAPI & Frontend Client Contract

## Summary

T42 added the first working OpenAPI/client contract gate. The repository now has a deterministic OpenAPI artifact, generated TypeScript contract types, a shared API client workspace package, and CI wiring that fails when generated contract files drift.

## Delivered

- Added `qa/openapi-contract.mjs` generator/checker.
- Added `qa/openapi-contract.test.mjs` contract assertions.
- Added `docs/api/openapi.json`.
- Added `packages/api-client/src/generated/openapi-types.ts`.
- Added `@discord-clone/api-client` with request id/auth propagation and error normalization.
- Added root scripts `openapi:write` and `openapi:check`.
- Added `npm run openapi:check` to the frontend CI job.
- Updated `qa/ci-workflow.contract.ps1`.
- Updated `package-lock.json` for the new workspace.

## Verification

- `node qa/openapi-contract.test.mjs`: PASS
- `npm run openapi:check`: PASS
- `npm test -w packages/api-client`: PASS, 2 tests
- `pwsh qa/ci-workflow.contract.ps1`: PASS
- `npm install --package-lock-only`: PASS

## Coverage

- representative public endpoint contract:
  - auth login/logout
  - guild create/channel create
  - message list/create
  - voice join
  - Gateway event polling
  - CSP report endpoint
- standard error response schema
- `X-Request-Id` contract
- generated API path/operation types
- client wrapper request headers and error normalization
- CI drift gate

## Residual Risks

- The spec is generated from a deterministic repository script, not directly from Spring runtime metadata.
- Existing web stores still call the older `apps/web/services/discord-api.ts`.
- Full REST endpoint inventory is not yet represented in `openapi.json`.
- Backend controllers still need a standard error envelope migration to fully match `ApiErrorResponse`.

## Next Recommended Task

Continue T42 with runtime/controller alignment: either add Spring OpenAPI extraction or a controller inventory comparison, then migrate the web REST client to `@discord-clone/api-client`.
