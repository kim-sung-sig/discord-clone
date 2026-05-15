# T25 CI QA Harness Wiring Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T25 CI QA Harness Wiring

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T24 real-backend QA와 T22 warning scan은 로컬에서 반복 가능하지만 CI workflow로 강제되지 않는다. |
| Solution | GitHub Actions workflow를 추가하고 PowerShell QA scripts를 Linux/Windows 양쪽에서 실행 가능하게 만든다. |
| Function UX Effect | PR/push에서 backend, frontend, real-backend smoke, warning artifacts가 자동으로 검증된다. |
| Core Value | 수동 QA evidence를 지속적 품질 게이트로 승격해 regression 발견 시간을 줄인다. |

## Scope

- Add `.github/workflows/ci.yml`.
- Add static CI workflow contract test.
- Make `qa/real-backend-e2e.ps1` cross-platform for GitHub Ubuntu runners.
- Make `qa/toolchain-warning-scan.ps1` cross-platform for PowerShell Core.
- Configure PostgreSQL service with `dev_user` / `dev_password` / `discord`.
- Upload QA artifacts for warning scan and real-backend e2e.

## Out of Scope

- Publishing Docker images.
- Deployment to cloud infrastructure.
- Secrets-based production environment validation.
- Full browser matrix beyond Chromium.

## Success Criteria

- CI workflow includes backend test, frontend test/build, toolchain warning scan, and real-backend smoke jobs.
- CI runtime job provisions PostgreSQL service and runs T24 harness.
- QA artifact upload is configured for toolchain and real-backend logs.
- Contract test verifies workflow structure and script references locally.

## Failure Criteria

- CI only runs compile/build without runtime smoke.
- Real-backend harness remains Windows-only.
- CI failures lose artifact logs.
