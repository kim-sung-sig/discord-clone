# T03 Invite Report

작성일: 2026-05-13  
PDCA Phase: Report  
Slice: T03 invite

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | Guild membership flow가 수동 member add에만 의존해 invite 기반 join 정책을 검증할 수 없었다. |
| Solution | invite lifecycle module/API와 invite modal을 추가했다. |
| Function UX Effect | invite preview, max-use/expiry metadata, role grant skeleton, accept CTA가 shell에서 확인된다. |
| Core Value | 이후 message/voice 기능이 invite 기반 membership join flow 위에서 확장될 수 있다. |

## Implemented

- Invite create/delete/preview/accept API
- Expired invite rejection
- Deleted invite rejection
- Max uses enforcement with race-safety service test
- Same-member accept idempotency
- Temporary membership flag
- Role grant skeleton on accept
- Invite modal component and e2e assertions
- Review remediation: channel-bound invite creation, role grant authorization ceiling, pre-consumption role grant validation, expired/deleted invite delete idempotency, and dialog semantics

## Verification Evidence

```powershell
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

Results:

- Backend targeted RED before fix: 3 expected failures for invalid channel/role grant, role grant ceiling, expired delete idempotency
- Backend targeted GREEN after fix: `:backend:boot:test --tests com.example.discord.invite.InviteControllerTest` passed
- Backend full: `BUILD SUCCESSFUL in 24s`; 29 actionable tasks executed
- Frontend targeted RED before fix: invite modal role assertion failed with `undefined`
- Frontend component: 2 files passed, 6 tests passed
- Frontend e2e: 4 Playwright tests passed
- Frontend build: Nuxt production build completed
- Infra: Docker Compose config rendered successfully

## Next Slice

T04 Message should build on invite-created membership and guild permission gates:

- message create/update/delete
- cursor pagination
- mention parse
- pin/edit history
- permission denied read/write tests
