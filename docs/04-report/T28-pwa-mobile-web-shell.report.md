# T28 PWA & Mobile Web Shell Report

작성일: 2026-05-15  
Slice: T28 PWA & Mobile Web Shell

## Summary

T28 added a mobile PWA shell around the existing Nuxt Discord UI. The app now exposes install metadata, a minimal offline fallback shell, and a single-pane mobile navigation model for channels, chat, members, and voice while preserving the desktop multi-pane baseline.

## Completed

- Added PWA head metadata and manifest link.
- Added `manifest.webmanifest`, `sw.js`, and `offline.html`.
- Added `/pwa-shell` metadata route.
- Added mobile shell header and bottom navigation.
- Added runtime `data-platform-surface` gating so PWA single-pane CSS does not automatically affect desktop surfaces.
- Updated visual smoke expectations for the mobile single-pane IA.
- Added PWA mobile Playwright smoke.
- Added component test proving web shell consumes shared platform contracts.

## Verification

- `npm run test -w apps/web -- --run`: PASS
- `$env:NUXT_DEV_PORT='3015'; npm run e2e -w apps/web -- tests/e2e/pwa-mobile.spec.ts`: PASS
- `$env:NUXT_DEV_PORT='3016'; npm run e2e -w apps/web -- tests/e2e/visual-smoke.spec.ts`: PASS
- `$env:NUXT_DEV_PORT='3017'; npm run e2e`: PASS, 16 passed and 1 real-backend test skipped
- `npm run build -w apps/web`: PASS

## Next

- T29: add Tauri desktop shell after the T28 web baseline is green.
- T47 should later expand accessibility checks for full tablist/tabpanel semantics and focus management.
