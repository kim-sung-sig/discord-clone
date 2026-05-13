# T10 Emoji/Reactions/Stickers Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T10 Emoji/Reactions/Stickers

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 메시지 UX에 emoji, reaction, sticker, expression 권한이 없어 Discord의 감정 표현 기능을 검증할 수 없었다. |
| Solution | `backend:modules:expression`, `MANAGE_EXPRESSIONS` 권한, `/api/*/reactions` REST adapter, Nuxt `ReactionBar`/`ExpressionPanel`을 추가했다. |
| Function UX Effect | 사용자는 메시지 reaction을 추가/제거하고 count/idempotency를 확인하며 custom emoji/sticker catalog skeleton을 볼 수 있다. |
| Core Value | 이후 moderation/audit/gateway fanout/persistent reaction table로 확장 가능한 expression boundary가 마련됐다. |

## Verification Evidence

Commands:

```powershell
.\gradlew.bat :backend:modules:expression:test :backend:boot:test --tests com.example.discord.expression.ExpressionControllerTest --rerun-tasks
npm run test -w apps/web -- --run tests/components/app-shell.test.ts
npm run e2e -w apps/web -- tests/e2e/app-shell.spec.ts --grep "adds and removes a reaction"
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run
npm run build -w apps/web
npm run e2e -w apps/web
```

Results:

- T10 backend targeted: `BUILD SUCCESSFUL in 9s`; 31 actionable tasks executed
- T10 frontend targeted component: 1 file passed, 20 tests passed
- T10 frontend targeted e2e: 1 Playwright test passed
- Backend full: `BUILD SUCCESSFUL in 40s`; 53 actionable tasks executed
- Frontend component full: 4 files passed, 27 tests passed
- Frontend build: Nuxt production build completed with known sourcemap/Vue package warnings
- Frontend e2e full: 11 Playwright tests passed

## Commits

- `4847d69 docs: plan T10 emoji reactions stickers`
- `4da1378 feat: add expression backend domain api`
- `8fff48a feat: add reaction expression ui`
