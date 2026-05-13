# T06 Nuxt Discord Shell Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T06 Nuxt Discord Shell

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| Server rail, channel sidebar, chat viewport, member sidebar, user panel | Met | Existing Nuxt shell regions retained and covered by component/e2e assertions |
| Responsive layout | Met | <=720px layout keeps channel sidebar visible and visual smoke checks mobile visibility |
| Storybook story coverage | Met | Framework-light `.stories.ts` modules added for 7 shell components plus invite modal; story index component test covers metadata |
| API/Gateway real integration seams | Met | `discord-api.ts`, `gateway-client.ts`, and shell store dispatch application tests cover REST path/client and dispatch mapping |
| Playwright login/guild/channel/message flow | Met | Existing shell E2E plus new visual smoke send-message flow pass |
| Visual smoke screenshot | Met | Playwright captures desktop/mobile screenshots under ignored `apps/web/test-results/visual-smoke` and asserts non-empty files |

## Gap Log

- Resolved: mobile shell hid channel navigation at <=720px; CSS now keeps channel sidebar visible and scrollable.
- Resolved: API helper surface was too internal/nested for Nuxt shell usage; flat helper aliases were added while keeping nested helpers.
- Resolved: Gateway dispatch contract rejected valid UI events without `createdAt`; normalization now accepts missing timestamp with deterministic fallback.
- Resolved: REST client expected only `fetch`; it now also accepts `fetcher` for testable client injection.

## Residual Risks

- Story modules are Storybook-compatible metadata files, but full Storybook runtime installation is intentionally deferred to avoid dependency churn.
- Visual smoke is screenshot existence/visibility based; pixel-diff baselines are deferred until a stable design baseline is approved.
