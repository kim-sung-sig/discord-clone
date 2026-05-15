# T31 Remote CI Verification Release Gate Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T31 Remote CI Verification & Release Gate

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T25에서 CI workflow는 작성됐지만 실제 GitHub-hosted runner 실행 결과가 아직 검증되지 않았다. T29 이후 데스크톱 계약 테스트도 원격 프론트엔드 게이트에 포함되어야 한다. |
| Solution | CI 계약을 전체 npm 워크스페이스 테스트 기준으로 보정하고, 브랜치를 원격에 푸시해 GitHub Actions 결과와 artifact 수집성을 확인한다. |
| Function UX Effect | 채팅/채널/모바일/데스크톱 UI 변경이 원격 게이트에서 동일하게 막히므로 사용자 화면 회귀가 main 병합 전에 발견된다. |
| Core Value | 로컬 통과 상태를 원격 릴리즈 게이트로 승격해 runner 차이, 경로 차이, 서비스 컨테이너 차이를 조기에 제거한다. |

## Scope

- Update CI frontend gate from web-only tests to all npm workspace tests.
- Update local CI workflow contract to enforce workspace-level frontend coverage.
- Push the active feature branch to GitHub.
- Observe the GitHub Actions `ci` run for backend, frontend, qa-runtime, and qa-toolchain jobs.
- If remote CI fails, collect failed logs/artifacts, document feedback, fix the defect, and rerun.
- Record remote run URL, job conclusions, and residual risks in T31 analysis/report documents.

## Out of Scope

- Production deployment.
- Docker image publishing.
- Release tagging.
- Native mobile build signing.
- Tauri binary release packaging.

## Success Criteria

- `qa/ci-workflow.contract.ps1` passes locally.
- `npm test --workspaces` is part of the GitHub Actions frontend job.
- The active branch is pushed to `origin`.
- A GitHub Actions `ci` run executes for the pushed branch.
- Backend, frontend, qa-runtime, and qa-toolchain job conclusions are recorded.
- Any remote-only failure has a feedback note and either a fix commit or explicit blocker.

## Failure Criteria

- CI remains web-only and does not run shared/desktop contract tests.
- Only local verification exists with no remote GitHub Actions evidence.
- Failed remote jobs cannot be diagnosed because logs or artifacts are not collected.
- T31 closes while a runner-specific issue remains undocumented.
