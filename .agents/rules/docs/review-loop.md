---
id: documentation-review-loop
description: 계획·설계 문서의 결함 수정과 재검수 순서를 고정한다.
appliesTo: [docs, backend, qa, infra]
triggers: [plan, design, review, architecture, kubernetes, msa]
---
# 계획·설계 검수 루프

1. 검수 시작 전에 대상 문서의 Git SHA 또는 working-tree revision과 검수 범위를 기록한다.
2. 그 revision에는 문서 편집을 하지 않은 채 독립 리뷰 결과를 모두 수집한다.
3. P0/P1/P2를 한 결함 목록으로 합치고, P0/P1을 모두 수정한다.
4. 수정 후 새 revision을 고정하고 다음 검수를 시작한다. 수정 중인 문서를 병렬 리뷰에 넘기지 않는다.
5. 통과는 같은 고정 revision에서 모든 선언된 리뷰가 기준 점수 이상이고 P0/P1이 없을 때만 선언한다.
6. 불합격 revision은 커밋·푸시·구현 착수의 근거로 사용하지 않는다. 다음 루프를 끝까지 수행하거나, 사용자에게 미완료 상태를 명확히 보고한다.
