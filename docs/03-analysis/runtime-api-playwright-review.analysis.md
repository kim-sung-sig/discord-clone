# Runtime API and Playwright Review Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: Runtime API/Playwright Review

## Runtime Services

- Backend service: `.\gradlew.bat :backend:boot:bootRun`, running on `http://127.0.0.1:8080`.
- Frontend service: Playwright `webServer` runs `npm run dev` on `http://127.0.0.1:3000`.

## Evidence

| Area | Command | Result |
| --- | --- | --- |
| Runtime API smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/api-smoke.ps1` | PASS |
| Playwright E2E | `npm run e2e -- tests/e2e/app-shell.spec.ts` | PASS, 13 tests |

## API Coverage Reviewed

- Auth signup/profile.
- Guild/channel/member visibility.
- Message create/list/pin/search and AutoMod block path.
- Invite create/preview.
- Gateway identify/heartbeat/events.
- Emoji creation and reactions.
- Thread create/archive/reopen and forum tag/post.
- Audit log access.
- Voice join/state/leave.
- Stage request/approve/audience transition.
- Soundboard create/play event.
- Premium catalog/quests/entitlement gate.
- Operational hardening headers on API responses.

## Findings

- No functional API failure reproduced after correcting the runtime smoke data to match controller contracts.
- No Playwright regression reproduced.
- Improvement found: the API runtime smoke was ad-hoc and not checked into the repository, so future runtime reviews would be hard to repeat consistently.
