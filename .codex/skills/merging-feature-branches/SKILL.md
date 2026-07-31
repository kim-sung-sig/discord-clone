---
name: merging-feature-branches
description: Use when a reviewed task branch is ready to merge into main and be deleted.
---
# Merging Feature Branches

## Gate

정확히 `task_<id>-<slug>` 형식의 branch만 대상이다. `main` 직접 commit/push는 금지하며, 이 절차의 승인된 `--no-ff` merge commit과 그 push만 허용한다.

merge 전 `docs/03-analysis/task_<id>-merge-review.md`에서 확인한다.

- 승인된 plan/blueprint
- artifact의 `base origin/main SHA`, `reviewed content SHA`, review/verification 대상 SHA
- spec review와 quality/security review의 preset·점수·P0/P1/P2
- 모든 선언 verification gate 성공
- `git diff --check` 성공
- `git diff --cached --name-only`가 비어 있고, `git diff --name-only origin/main...reviewed-content`가 blueprint의 `Expected Changed Files` 중 merge-review artifact를 제외한 목록과 일치함
- residual risk

spec review와 quality/security review가 각각 90/100 미만이거나 P0/P1이 있거나 verification이 실패하면 `main` merge/push를 금지한다. `main` merge/push에는 사용자 승인 예외가 없다.

review artifact도 독립 검수 대상이다. 검수자가 review 누락·잘못된 score·불충분한 verification을 발견하면 merge하지 않는다. 같은 task branch에서 수정 → fresh spec review → fresh quality/security review → artifact 갱신을 반복한다.

## Procedure

1. task branch와 `main`의 clean 상태를 read-only로 확인하고 `git fetch origin` 후 local `main`이 `origin/main`과 같은 SHA인지 확인한다. 다르면 중단한다.
2. `git merge-base --is-ancestor origin/main task_<id>-<slug>`가 성공하는지 확인한다. 실패하면 task branch에 최신 `main`을 반영하고 fresh review·verification을 다시 수행한다.
3. artifact의 base SHA가 `origin/main`, review/verification 대상 SHA가 `reviewed content SHA`와 정확히 같은지 대조한다. 현재 `git rev-parse task_<id>-<slug>` evidence artifact commit의 parent가 reviewed content SHA이고 `git diff --name-only reviewed-content..task_<id>-<slug>`가 merge-review artifact 하나뿐인지 확인한다. 불일치하면 fresh review·verification을 다시 수행한다.
4. task branch의 review artifact와 `git diff --name-only origin/main...reviewed-content`를 blueprint의 `Expected Changed Files`와 대조한다.
5. `main`으로 전환 후 항상 `git merge --no-ff task_<id>-<slug>`를 사용한다.
6. merge commit 검증 후 `main` push하고, `origin/main`에 merge SHA가 포함됐는지 확인한다.
7. 확인 뒤에만 `git push origin --delete task_<id>-<slug>`로 remote branch를 삭제하고, `git branch -d task_<id>-<slug>`로 local task branch를 삭제한다.
8. 최종 보고: branch, merge SHA, review score, verification, residual risk.

## Do Not

- review artifact 없이 merge하지 않는다.
- task 외 dirty file을 stage/commit하지 않는다.
- merge 전 task branch를 삭제하지 않는다.
