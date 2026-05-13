# T05 Gateway Core Report

작성일: 2026-05-13  
PDCA Phase: Report  
Slice: T05 gateway core

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | Gateway 세션/heartbeat/resume/event sequence 모델이 없어 실시간 UX와 이후 presence/voice fanout을 검증할 수 없었다. |
| Solution | in-memory gateway core와 HTTP test adapter, Nuxt gateway status panel을 추가하고 리뷰에서 발견된 backlog/closed-session/publish/stale-sequence 결함을 보강했다. |
| Function UX Effect | 화면에서 READY, last sequence, heartbeat ACK, resumed state, gateway event log를 확인할 수 있다. |
| Core Value | T06 이후 실제 WebSocket transport와 presence/typing/voice signaling이 재사용할 gateway session semantics가 마련됐다. |

## Verification Evidence

Commands:

```powershell
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

Results:

- Backend full: `BUILD SUCCESSFUL in 36s`; 37 actionable tasks executed
- Frontend component: 2 files passed, 10 tests passed
- Frontend e2e: 5 Playwright tests passed
- Frontend build: Nuxt production build completed with known sourcemap/package deprecation warnings
- Infra: Docker Compose config rendered successfully
