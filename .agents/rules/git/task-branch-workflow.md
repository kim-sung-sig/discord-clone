---
id: git-task-branch-workflow
description: Task branch, review, verification, and push policy.
alwaysApply: true
appliesTo:
  - git
triggers:
  - commit
  - push
  - merge
  - branch
---
# Git Task Branch Workflow

- 구현 시작 전 `main`에서 정확히 `task_<id>-<slug>` 형식의 branch를 만든다. 예: `task_T201-jwt-config`.
- `main`/`master`에서는 직접 commit하거나 push하지 않는다. 예외는 이 규칙의 모든 gate를 통과한 task branch를 `--no-ff` merge한 merge commit과 그 `main` push뿐이다.
- task branch commit은 승인된 blueprint의 `Expected Changed Files`에 적힌 task 소유 파일만 포함한다. evidence artifact를 별도 commit하는 경우 그 artifact만 예외다. 기존 dirty work와 다른 task 문서는 포함하지 않는다.
- task branch push는 아래 조건이 모두 충족될 때 허용한다.
  - 승인된 plan/blueprint 존재
  - spec review와 quality/security review가 각각 90/100 이상
  - P0/P1 미해결 finding 없음
  - 선언된 verification gate 성공
  - `git diff --check` 성공
- 위 조건이 실패하면 push하지 않는다. 사용자가 branch와 실패 상태를 명시적으로 승인한 경우만 예외다.
- 예외 승인은 feature branch push에만 적용한다. `main`/`master` merge·push에는 적용하지 않는다.
- stage 전 `git diff --cached --name-only`가 blueprint의 `Expected Changed Files` 중 현재 commit 범위와 일치하는지 확인한다.
- 완료 task는 `merging-feature-branches` skill로 `main`에 merge하고, merge 성공·push 확인 후 task branch를 삭제한다.
