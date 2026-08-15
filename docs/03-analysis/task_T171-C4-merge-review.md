# T171-C4 병합 검토 증거

## 대상

- 작업 브랜치: `task_T171-C4-chat-scale`
- base `origin/main` / `main`: `9ea13919b15a270e10842970cdc524517fdb110a`
- reviewed content SHA: `710a3caa4e782b42a6d7edfe9692f1c2fe6ce25d`
- 최종 실측 실행 SHA: `fc3af9fb700d100d410669d7b718d23f479ff76d`
- artifact: `qa/artifacts/c4-chat-scale/20260813-213400-815`

## 승인된 범위

단일 리전 PostgreSQL 채팅 확장성 하네스, baseline/date_range/date_hash 비교, primary/replica lag·cursor·pruning·relation size·vacuum 증거와 fail-closed verifier, 한국어 설계·계획·검증 보고서다. 운영 스키마 마이그레이션, 자동 shard cutover, 다중 리전은 제외했다.

## 독립 검토

- reviewer: `t171c4_task3_latency_fix`
- score: **98/100**
- P0: 0
- P1: 0
- P2: 1 — `routing.tsv`가 required 배열에는 없지만 header read가 `ErrorAction Stop`으로 즉시 실패하여 실질적으로 fail-closed다.
- 판정: 승인 가능

## 검증 증거

```text
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
C4_CHAT_SCALE_CONTRACT_PASS

pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/20260813-213400-815
C4_CHAT_SCALE_VERIFY_PASS
decision=ACCEPT
candidate_decision=DEFER
selected_variant=baseline
```

- latency: 36 unique rows (3 variants × 4 phases × 3 operations)
- plans: 9개, cursor gap: 0
- replica lag p95: 7.461ms, 음수 샘플 0개
- db-stats: 475개 샘플, relation/index size·테이블별 vacuum 안정성 통과
- routing: 5개 시나리오 모두 통과, `routing_pass=true`
- hot-room write QPS: baseline 3062.04, date_range 2789.62, date_hash 2681.12; `hash_load_gate=true`
- `docker compose --profile load -f qa/c4-chat-scale/docker-compose.yml config`: 성공
- `git diff --check`: 성공

## 운영 판정

baseline steady/hot-room write p99 400.915/411.002ms로 모두 500ms 미만이고 안정성 게이트도 통과했다. 따라서 partition 후보는 `DEFER`, 선택 variant는 `baseline`이다. date_range steady write p99 583.926ms, date_hash 389.940ms는 비교 증거로 남긴다.

## 병합 허용

승인된 계획·범위, 독립 리뷰 90점 이상, P0/P1 없음, 계약·실측 verifier·Compose·diff 검증을 모두 충족했다. 이 evidence commit에는 본 파일만 포함해야 하며, 이후 main 병합은 repository merge workflow로 수행한다.
