# T07 Friendship/DM/Group DM Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T07 Friendship/DM/Group DM

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 사용자 간 친구/차단/DM/group DM 모델이 없어 Discord의 개인 커뮤니케이션 핵심 흐름을 구현할 기반이 없었다. |
| Solution | `backend:modules:social`과 `/api/social/*` REST adapter를 추가하고, Nuxt shell에 DM sidebar와 group call skeleton을 연결했다. |
| Function UX Effect | 화면에서 친구/차단/DM/group DM/call 상태를 확인하고 group DM 멤버 추가/삭제 및 call 시작 흐름을 검증할 수 있다. |
| Core Value | T08 presence/typing/read state와 이후 voice/group call 기능이 사용할 private channel membership/policy 기반이 생겼다. |

## Verification Evidence

Commands:

```powershell
.\gradlew.bat :backend:modules:social:test :backend:boot:test --tests com.example.discord.social.SocialControllerTest --rerun-tasks
npm run test -w apps/web -- --run tests/components/app-shell.test.ts tests/components/story-index.test.ts
npm run e2e -w apps/web -- tests/e2e/app-shell.spec.ts
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run
npm run build -w apps/web
npm run e2e -w apps/web
```

Results:

- T07 backend targeted: `BUILD SUCCESSFUL in 33s`; 25 actionable tasks executed
- T07 frontend targeted component: 2 files passed, 13 tests passed
- T07 frontend targeted e2e: 5 Playwright tests passed
- Backend full: `BUILD SUCCESSFUL in 40s`; 41 actionable tasks executed
- Frontend component full: 4 files passed, 19 tests passed
- Frontend build: Nuxt production build completed with known sourcemap/Vue package warnings
- Frontend e2e full: 7 Playwright tests passed

## Commits

- `7b7fbfe docs: plan T07 friendship dm group dm`
- `57281f5 feat: add social backend domain api`
- `bda2986 feat: add nuxt dm shell`
