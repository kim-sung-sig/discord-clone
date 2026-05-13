# T06 Nuxt Discord Shell Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T06 nuxt discord shell

## Frontend Design

### API/Gateway Seam

- Add `apps/web/services/discord-api.ts` with endpoint builders and a minimal `DiscordRestClient` interface.
- Add `apps/web/services/gateway-client.ts` with event type guards and apply contract.
- Store keeps deterministic seed state but exposes hydration/apply actions that consume these contracts.
- Tests verify endpoint paths and duplicate/stale event behavior.

### Stories

- Add framework-light `.stories.ts` files beside existing components.
- Each story exports metadata and render args so future Storybook installation can consume them.
- Add a Vitest story index test that imports all story files and asserts required metadata.

### Layout

- Preserve current visual direction.
- Add mobile gateway/invite/user panel ordering so app shell remains navigable.
- Add keyboard focus assertions for channel buttons and composer.

### Visual Smoke

- Add Playwright smoke test that captures:
  - `apps/web/test-results/visual-smoke/desktop-shell.png`
  - `apps/web/test-results/visual-smoke/mobile-shell.png`
- Assertions verify screenshots are non-empty and core landmarks are visible/collapsed intentionally.

## QA Design

- Component tests: story metadata, API client paths, keyboard focus, gateway event apply contract.
- E2E tests: login -> app, channel select, send message, visual smoke screenshots.
- Build and existing full gates remain mandatory.
