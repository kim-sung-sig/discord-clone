# T08 Presence/Typing/Read State Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T08 Presence/Typing/Read State

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 채팅/DM 기능은 있었지만 presence, typing expiry, read marker, unread count가 없어 실시간 커뮤니케이션 상태 UX가 부족했다. |
| Solution | `backend:modules:presence`와 `/api/presence/*` REST adapter를 추가하고, Nuxt shell에 presence/typing/unread badge UI를 연결했다. |
| Function UX Effect | 멤버 상태, typing indicator, unread badge, mark-read 흐름을 화면과 테스트에서 확인할 수 있다. |
| Core Value | Gateway fanout과 persisted message/read state로 확장 가능한 TTL/read 모델 기반이 마련됐다. |

## Verification Evidence

Commands:

```powershell
.\gradlew.bat :backend:modules:presence:test :backend:boot:test --tests com.example.discord.presence.PresenceControllerTest --rerun-tasks
npm run test -w apps/web -- --run tests/components/app-shell.test.ts
npm run e2e -w apps/web -- tests/e2e/app-shell.spec.ts
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run
npm run build -w apps/web
npm run e2e -w apps/web
```

Results:

- T08 backend targeted: `BUILD SUCCESSFUL in 11s`; 27 actionable tasks executed
- T08 frontend targeted component: 1 file passed, 16 tests passed
- T08 frontend targeted e2e: 7 Playwright tests passed
- Backend full: `BUILD SUCCESSFUL in 39s`; 45 actionable tasks executed
- Frontend component full: 4 files passed, 23 tests passed
- Frontend build: Nuxt production build completed with known sourcemap/Vue package warnings
- Frontend e2e full: 9 Playwright tests passed

## Commits

- `0c11ed2 docs: plan T08 presence typing read state`
- `5191702 feat: add presence backend domain api`
- `f5474ba feat: add presence typing unread ui`
