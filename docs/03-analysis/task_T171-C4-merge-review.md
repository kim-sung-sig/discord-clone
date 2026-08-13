# T171-C4 병합 검토 증거

## 대상

- 작업 브랜치: `task_T171-C4-chat-scale`
- base `origin/main` / `main`: `9ea13919b15a270e10842970cdc524517fdb110a`
- reviewed content SHA: `dba60fb090f21b0fc347cadad7374b2c1737ef47`
- 최종 실측 실행 SHA: `4767d6f7ac169333adb341404f38c6ddd2c148dd`
- artifact: `qa/artifacts/c4-chat-scale/20260813-165819-087`

## 승인된 범위

단일 리전 PostgreSQL 채팅 확장성 하네스, baseline/date_range/date_hash 비교, primary/replica lag·cursor·pruning·relation size·vacuum 증거와 fail-closed verifier, 한국어 설계·계획·검증 보고서다. 운영 스키마 마이그레이션, 자동 shard cutover, 다중 리전은 제외했다.

## 독립 검토

- reviewer: `t171c4_task3_latency_fix`
- score: **96/100**
- P0: 0
- P1: 0
- P2: 1 — verifier가 `db-stats.tsv` table allowlist를 검사하지 않음. 실행기가 artifact를 직접 생성하며 운영 채택을 막지 않는 제한적 잔여 위험이다.
- 판정: 승인 가능

## 검증 증거

```text
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
C4_CHAT_SCALE_CONTRACT_PASS

pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/20260813-165819-087
C4_CHAT_SCALE_VERIFY_PASS
decision=ACCEPT
candidate_decision=DEFER
selected_variant=baseline
```

- latency: 36 unique rows (3 variants × 4 phases × 3 operations)
- plans: 9개, cursor gap: 0
- replica lag p95: 9.245ms
- db-stats: 474개 샘플, relation/index size·테이블별 vacuum 안정성 통과
- `docker compose --profile load -f qa/c4-chat-scale/docker-compose.yml config`: 성공
- `git diff --check`: 성공

## 운영 판정

baseline steady write p99 118.368ms, hot-room write p99 393.504ms로 500ms 미만이고 안정성 게이트도 통과했다. 따라서 partition 후보는 `DEFER`, 선택 variant는 `baseline`이다. baseline recovery/write p99 1287.255ms는 별도 회복성 최적화 과제로 남긴다.

## 병합 허용

승인된 계획·범위, 독립 리뷰 90점 이상, P0/P1 없음, 계약·실측 verifier·Compose·diff 검증을 모두 충족했다. 이 evidence commit에는 본 파일만 포함해야 하며, 이후 main 병합은 repository merge workflow로 수행한다.
