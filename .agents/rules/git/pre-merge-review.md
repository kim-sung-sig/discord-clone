---
id: git-pre-merge-review
description: Required acceptance evidence before merging a task branch.
alwaysApply: true
appliesTo:
  - git
triggers:
  - merge
  - pull request
  - pre-merge
---
# Pre-Merge Review Gate

정확히 `task_<id>-<slug>` 형식의 branch를 `main`에 merge하기 전 아래 증거를 확인한다.

- task branch 이름, base `main` commit, 대상 commit SHA
- 승인된 plan/blueprint 경로
- spec compliance review: preset, score, P0/P1/P2, acceptance
- code quality/security review: preset, score, P0/P1/P2, acceptance
- verification 명령과 성공 결과
- residual risk와 명시적 defer 항목

증거는 `docs/03-analysis/task_<id>-merge-review.md`에 한국어로 기록한다. artifact에는 `base origin/main SHA`, `reviewed content SHA`, 두 review와 verification의 대상 SHA를 기록한다. `base origin/main SHA`는 현재 `origin/main`과, review/verification 대상 SHA는 `reviewed content SHA`와 각각 일치해야 한다. 현재 task branch tip은 evidence artifact commit으로 외부에서 계산하며, 그 parent는 `reviewed content SHA`이고 해당 commit은 merge-review artifact만 변경해야 한다. spec review와 quality/security review가 각각 90/100 이상이고 P0/P1이 없으며 verification이 성공할 때만 merge를 허용한다. `main`/`master` merge·push에는 승인 예외가 없다. merge 후 발견된 review는 acceptance 증거가 아니며, 별도 fix task branch를 만든다.

## Review Correction Loop

- 구현 review와 merge-review artifact는 서로 독립적으로 검수한다. review가 존재한다는 사실만으로 acceptance가 되지 않는다.
- 독립 검수가 P0/P1, 누락된 verification, 잘못된 score, 범위 누락을 찾으면 task는 미완료다.
- artifact의 base/reviewed content SHA 관계가 다르거나 evidence artifact commit에 merge-review artifact 외 변경이 있거나 `origin/main`이 task branch의 조상이 아니면 task는 미완료다. 현재 branch tip으로 변경을 반영한 뒤 fresh review와 verification을 다시 수행한다.
- 같은 `task_<id>-<slug>` branch에서 원인을 수정하고, 새 evidence를 기록한 뒤 fresh reviewer로 spec review → quality/security review를 다시 수행한다.
- 이 loop가 모든 declared threshold 충족, P0/P1 없음, verification 성공으로 끝날 때까지 merge/push-to-main은 금지다.
