# T13 Voice/SFU Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T13 Voice/SFU

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | voice channel은 UI 라벨만 있고 join/leave, state, token, event boundary가 없었다. |
| Solution | `backend:modules:voice`, permission-gated voice REST API, deterministic LiveKit skeleton token, Nuxt `VoicePanel`을 추가했다. |
| Function UX Effect | 사용자는 voice channel join/leave, mute/deaf/speaking/screen share skeleton, participant list, voice event log를 확인할 수 있다. |
| Core Value | 실제 LiveKit/WebRTC 통합 전 Spring authorization과 voice state authority가 고정됐다. |

## Verification Evidence

Commands:

```powershell
.\gradlew.bat :backend:modules:voice:test --rerun-tasks
.\gradlew.bat :backend:boot:test --tests com.example.discord.voice.VoiceControllerTest --rerun-tasks
.\gradlew.bat test
npm run test -- --run
npm run build
npm run e2e -- tests/e2e/app-shell.spec.ts
```

Results:

- Backend domain targeted: `BUILD SUCCESSFUL in 1s`; 3 actionable tasks executed.
- Backend REST targeted: `BUILD SUCCESSFUL in 9s`; 35 actionable tasks executed.
- Backend full: `BUILD SUCCESSFUL in 30s`; 65 actionable tasks, 1 executed.
- Frontend full Vitest: 4 files passed, 34 tests passed.
- Frontend build: Nuxt production build completed with known sourcemap/Vue package warnings.
- Frontend E2E: 12 Playwright tests passed.

## Commits

- `a577022 docs: plan T13 voice sfu`
- `6b8c0c8 feat: add voice operations ui`
- `7c0e02b feat: add voice sfu backend skeleton`
- pending: `docs: record T13 voice sfu PDCA`
