# T62 Gateway Subscription Reconciliation Analysis

Date: 2026-05-21

## Findings

| Finding | Result |
| --- | --- |
| Resume on another node did not re-register stream subscriptions | `resume` now calls `registerSubscriptions(session)`. |
| Newly visible channels after identify were not subscribed until reconnect | `poll` now calls `registerSubscriptions(session)`. |
| Hidden channel filtering still needs to be delivery-time | Existing `canDeliver` filtering remains unchanged. |
| Visual report was requested | Added screenshot capture spec and HTML report based on the Desktop Discord template style. |

## RED Evidence

```powershell
.\gradlew.bat :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest
```

Failed first on:

- `resumeOnDifferentNodeRegistersVisibleChannelSubscriptions`
- `pollReconcilesChannelsThatBecameVisibleAfterIdentify`

## GREEN Evidence

```powershell
.\gradlew.bat :backend:modules:gateway:test --tests com.example.discord.gateway.InMemoryGatewayServiceTest
.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.CentralRedisGatewayFanoutSmokeTest
$env:NUXT_DEV_PORT='3032'; $env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:3032'; $env:CI='1'; npm run e2e -w apps/web -- --project=chromium --workers=1 t62-screenshot-report.spec.ts
powershell -ExecutionPolicy Bypass -File qa\central-redis-smoke.ps1
.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest :backend:modules:gateway:checkstyleMain :backend:modules:gateway:checkstyleTest
git diff --check
```

Passed. `git diff --check` reported CRLF conversion warnings only.

## Screenshot Artifacts

- `output/playwright/t62-subscription-reconciliation/01-desktop-gateway-shell.png`
- `output/playwright/t62-subscription-reconciliation/02-voice-state.png`
- `output/playwright/t62-subscription-reconciliation/03-stage-premium.png`
- `output/playwright/t62-subscription-reconciliation/04-mobile-chat.png`
- `output/playwright/t62-subscription-reconciliation/05-report-html-render.png`
