# T166 Discord Shell Layout Compression Pass Design

Date: 2026-05-21
Status: Completed

## Approach

The shell keeps the existing six-column Discord-style workspace, but tightens the desktop column tracks and makes operational panel internals wrap inside their assigned column.

## CSS Changes

- Hide `.skip-link` off-canvas until focused so accessibility remains intact without consuming a visible grid row.
- Reduce desktop `.workspace` column widths for channel/sidebar/member/forum/admin panels.
- Reduce internal padding and gaps for role permission, moderation, gateway, and voice panels.
- Change gateway status cards and voice controls to smaller grid tracks that can shrink with `minmax(0, 1fr)`.
- Force role permission diff, preview-as-role, privileged audit, and moderation audit entries to render as grid/block content with `overflow-wrap: anywhere`.

## Test Design

`apps/web/tests/e2e/layout-compression.spec.ts` measures real browser layout at `1366x768` and fails when:

- document scroll width exceeds viewport width,
- workspace right edge exceeds viewport width,
- selected operational panels, cards, audit rows, or buttons overflow horizontally,
- controls sit outside the visible viewport.

The test writes a full-page screenshot to `output/playwright/t166-layout-compression/desktop-layout.png` for manual review.

## Known Caveat

Local Playwright runs must use an isolated `NUXT_DEV_PORT` and matching `PLAYWRIGHT_BASE_URL` with `CI=1`; otherwise Playwright can reuse an unrelated process already listening on port 3000.
