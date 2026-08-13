# T171-C4 채팅 중심 PostgreSQL 확장성 검증

현재 검증기는 `qa/c4-chat-scale/verify.ps1`가 생성한 `decision.json`을 기준으로 동작한다. baseline 1분 smoke(`qa/artifacts/c4-chat-scale/20260813-135513-641`)는 오류율 0%, p99 11.001ms, replica lag 최대 0초, cursor 중복 0을 기록했다.

독립 최종 리뷰는 commit `4767d6f` 기준 9.5/10, P0/P1 0건으로 승인됐다(P2: 실행기가 직접 생성하는 `db-stats.tsv`의 table allowlist 미검증). 검증기는 unknown variant, 누락/비수치 lag, NaN·Infinity·음수 수치, percentile 순서, `error_rate = error_count / count`, 9개 plan evidence, 3개 variant cursor evidence, relation/index size·vacuum 안정성을 모두 fail-closed로 검사한다. 운영 판정은 40분 실측이 없거나 새 `db-stats.tsv` 안정성 증거가 없으면 의도적으로 `REJECT:NOT_RUN`이다.

러너는 각 phase의 총 시간을 operation별로 write 50%·history 35%·search 15%로 분배한다. 이전 구현처럼 operation마다 phase 시간을 반복하지 않으므로 `all + 40`은 variant당 40분(전체 약 120분)이다.

## 40분 통합 실측 결과

아티팩트: `qa/artifacts/c4-chat-scale/20260813-165819-087` (36개 latency 행, variant별 12행, plan 9개, cursor gap 각 0, replica lag p95 9.245ms, db-stats 474개 샘플, relation/index size·vacuum 안정성 통과). 실행 commit SHA는 `4767d6f`이며 verifier 실행 결과는 `C4_CHAT_SCALE_VERIFY_PASS`다.

| variant | 최대 p99 | 해당 구간 | 판정 |
|---|---:|---|---|
| baseline | 1287.255ms | recovery/write | 회복성 리스크 |
| date_range | 537.051ms | hot-room/write | 후보 기준 초과 |
| date_hash | 317.266ms | recovery/write | 기준 이내 |

전체 후보의 raw 최대 p99에는 baseline recovery와 date_range hot-room 초과가 있지만, partition 채택 기준은 baseline의 steady·hot-room write p99다. baseline은 steady 118.368ms, hot-room 393.504ms로 모두 500ms 미만이고 relation/vacuum 안정성도 통과했다. 따라서 최종 결정은 `ACCEPT`, 후보 결정은 `DEFER`, 선택 variant는 `baseline`이다. date_hash의 hot-room write p99 212.383ms 개선은 기록하되, baseline 보류 조건을 만족하므로 운영 partition으로 채택하지 않는다. baseline recovery/write p99 1287.255ms는 별도 회복성 최적화 과제로 남긴다.

실행 명령:

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

판정 기준은 오류율 1% 미만, baseline steady·hot-room write p99 500ms 미만, partition pruning 증거, replica lag p95 2초 미만, relation/index size 50GB 이하, 테이블별 vacuum 비감소 및 dead/live 10% 이하이다. 이번 run의 미실행 항목은 없다. 운영 스키마 마이그레이션·자동 shard cutover는 C4 범위 밖이므로 별도 task로 남긴다.
