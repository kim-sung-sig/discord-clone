# T28 PWA & Mobile Web Shell Analysis

작성일: 2026-05-15  
Slice: T28 PWA & Mobile Web Shell

## Verification Matrix

| Gate | Command / Review | Result | Notes |
| --- | --- | --- | --- |
| Web unit/component tests | `npm run test -w apps/web -- --run` | PASS | 6 files, 42 tests passed. |
| PWA mobile smoke | `$env:NUXT_DEV_PORT='3015'; npm run e2e -w apps/web -- tests/e2e/pwa-mobile.spec.ts` | PASS | Manifest, theme-color, service worker asset, offline shell, metadata route, and mobile pane navigation verified. |
| Visual smoke | `$env:NUXT_DEV_PORT='3016'; npm run e2e -w apps/web -- tests/e2e/visual-smoke.spec.ts` | PASS | Desktop and mobile screenshots generated against updated single-pane IA. |
| Full web E2E | `$env:NUXT_DEV_PORT='3017'; npm run e2e` | PASS | 16 passed, 1 real-backend test skipped. |
| Web production build | `npm run build -w apps/web` | PASS | Nuxt build completed with known T22 warning budget warnings. |

## Review Findings And Actions

| Finding | Severity | Action |
| --- | --- | --- |
| Service worker cached all navigation HTML under `/` | P1 | Reduced SW to cache only manifest/offline shell and use offline fallback without persisting user-specific HTML. |
| Width-only mobile CSS could force desktop-app/web-desktop into PWA single-pane behavior | P1 | Added `data-platform-surface` gating so single-pane CSS applies only after the runtime classifies the surface as `pwa-mobile`. |
| Existing visual smoke expected channel sidebar visible after mobile resize | P1 | Updated visual smoke to assert mobile bar/chat first, then navigate to channel pane through mobile controls. |
| Mobile nav used mixed button/page semantics | P2 | Replaced `aria-current` with tab-style `aria-selected` and `role="tab"`. |
| Service worker/offline shell was not tested | P1 | Added `/sw.js`, `/offline.html`, and `/pwa-shell` assertions to PWA E2E. |

## Residual Risks

- Offline behavior is a shell fallback only; it does not cache API data or provide offline message state.
- The PWA manifest currently has no icons, so full installability scoring remains a later pass.
- Existing Nuxt sourcemap and Vue export warnings remain under the T22 warning budget.
