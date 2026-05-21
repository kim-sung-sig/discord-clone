# T166 Discord Shell Layout Compression Pass Report

Date: 2026-05-21
Status: Completed

## Result

The Discord shell desktop layout now keeps the forum, admin permission, gateway, moderation, voice, invite, and experience panels inside the viewport at the 1366px review size. The visible skip link gap was removed, right-side buttons no longer clip off-screen, and compact admin/audit text wraps without overlapping adjacent controls.

## Files Changed

- `apps/web/assets/css/main.css`
- `apps/web/tests/e2e/layout-compression.spec.ts`

## Verification

```powershell
$env:NUXT_DEV_PORT='3034'
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3034'
$env:CI='1'
npm.cmd run e2e -w apps/web -- --project=chromium --workers=1 layout-compression.spec.ts
# PASS: 1 test
```

```powershell
$env:NUXT_DEV_PORT='3033'
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3033'
$env:CI='1'
npm.cmd run e2e -w apps/web -- --project=chromium --workers=1 visual-smoke.spec.ts
# PASS: 1 test
```

## Screenshot Evidence

- `apps/web/test-results/visual-smoke/desktop-shell.png`
- `output/playwright/t166-layout-compression/desktop-layout.png`

## Wiki Updated

- `wiki/Frontend Client Architecture.md`
- `wiki/QA Infra Operations.md`
- `wiki/Current Roadmap And Risks.md`
- `log.md`
