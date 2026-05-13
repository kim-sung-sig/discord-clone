# T04 Message Core Report

작성일: 2026-05-13  
PDCA Phase: Report  
Slice: T04 message core

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 정적 채팅 shell과 서버 메시지 부재로 message lifecycle, 권한, pagination을 검증할 수 없었다. |
| Solution | in-memory message module/API와 Nuxt chat composer/metadata UI를 추가하고, 리뷰에서 발견된 권한/삭제/mention 결함을 회귀 테스트로 보강했다. |
| Function UX Effect | 채팅 화면에서 pinned/edited/deleted/mention 상태와 composer send flow가 검증된다. |
| Core Value | T05 Gateway fanout이 재사용할 message lifecycle 모델과 권한 경계가 마련됐다. |

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

- Backend full: `BUILD SUCCESSFUL in 30s`; 33 actionable tasks executed
- Frontend component: 2 files passed, 8 tests passed
- Frontend e2e: 5 Playwright tests passed
- Frontend build: Nuxt production build completed with known sourcemap/package deprecation warnings
- Infra: Docker Compose config rendered with `POSTGRES_USER=dev_user`, `POSTGRES_PASSWORD=dev_password`, `POSTGRES_DB=discord`
