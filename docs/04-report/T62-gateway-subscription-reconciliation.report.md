# T62 Gateway Subscription Reconciliation Report

Date: 2026-05-21

## Result

Completed.

## Changes

- `poll` now reconciles current visible guild/channel subscriptions.
- `resume` now reconciles current visible guild/channel subscriptions before replay delivery.
- Added focused service tests for cross-node resume subscription registration and newly visible channel subscription.
- Added central Redis fanout smoke coverage for resume-on-different-node stream subscription.
- Added Playwright screenshot capture for the screenshot-based report.
- Added HTML screenshot report using the Desktop `discord-clone-review.html` style as the template basis.

## Verification

```powershell
.\gradlew.bat :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.CentralRedisGatewayFanoutSmokeTest
$env:NUXT_DEV_PORT='3032'; $env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3032'; $env:CI='1'; npm run e2e -w apps/web -- --project=chromium --workers=1 t62-screenshot-report.spec.ts
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\ci-workflow.contract.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest :backend:modules:gateway:checkstyleMain :backend:modules:gateway:checkstyleTest
git diff --check
```

Passed. `git diff --check` reported CRLF conversion warnings only.

## Screenshot Report

- HTML: `docs/04-report/T62-subscription-reconciliation-screenshot-report.html`
- Render check: `output/playwright/t62-subscription-reconciliation/05-report-html-render.png`
- Source screenshots: `output/playwright/t62-subscription-reconciliation/`

## Security Review

Subscription reconciliation only registers stream interest. It does not change authorization or serialize new sensitive
data. Event delivery still filters hidden channels through `canDeliver`.
