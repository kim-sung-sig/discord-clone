# T171-C4 채팅 중심 PostgreSQL 확장성 검증

현재 검증기는 `qa/c4-chat-scale/verify.ps1`가 생성한 `decision.json`을 기준으로 동작한다. 1분 smoke는 계약·스크립트 연결 확인용이며, 운영 판정은 아래 40분 통합 실측만 사용한다.

검증기는 unknown variant, 누락/비수치 lag, NaN·Infinity·음수 수치, percentile 순서, `error_rate = error_count / count`, 9개 plan evidence, 3개 variant cursor evidence, relation/index size·vacuum 안정성을 모두 fail-closed로 검사한다. 또한 write/read-after-write는 primary에 고정하고, replica lag 2초 초과 시 history/search를 primary로 되돌리며, 30초 초과 시 `history_page_limited` 경고를 남긴다. 운영 판정은 40분 실측이 없거나 새 `db-stats.tsv` 안정성 증거가 없으면 의도적으로 `REJECT:NOT_RUN`이다.

러너는 각 phase의 총 시간을 operation별로 write 50%·history 35%·search 15%로 분배한다. 이전 구현처럼 operation마다 phase 시간을 반복하지 않으므로 `all + 40`은 variant당 40분(전체 약 120분)이다.

## 40분 통합 실측 결과

아티팩트: `qa/artifacts/c4-chat-scale/20260813-213400-815` (36개 latency 행, variant별 12행, plan 9개, cursor gap 각 0, routing 5개 시나리오, replica lag p95 7.461ms, 음수 lag 0개, db-stats 475개 샘플, relation/index size·vacuum 안정성 통과). 실행 commit SHA는 `fc3af9f`이며 verifier 실행 결과는 `C4_CHAT_SCALE_VERIFY_PASS`다.

| variant | 최대 p99 | 해당 구간 | 판정 |
|---|---:|---|---|
| baseline | 411.002ms | steady/hot-room write | 기준 이내 |
| date_range | 583.926ms | steady write | baseline 보류 기준 초과 |
| date_hash | 451.699ms | steady write | 기준 이내 |

partition 채택 기준은 baseline의 steady·hot-room write p99다. baseline은 steady 400.915ms, hot-room 411.002ms로 모두 500ms 미만이고 relation/vacuum 안정성도 통과했다. hot-room write QPS는 baseline 3062.04, date_range 2789.62, date_hash 2681.12였고 `hash_load_gate=true`였지만 baseline 보류 조건을 만족하므로 운영 partition은 채택하지 않는다. 따라서 최종 결정은 `ACCEPT`, 후보 결정은 `DEFER`, 선택 variant는 `baseline`이다. date_range의 steady write p99 583.926ms와 date_hash 389.940ms는 비교 증거로 보존한다.

라우팅 드릴은 write/read-after-write=primary, 정상 history=replica, lag 23.421초 history fallback=primary, lag 30.001초 severe fallback=primary 및 `history_page_limited` 경고를 모두 통과했다(`routing_pass=true`).

실행 명령:

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

판정 기준은 오류율 1% 미만, baseline steady·hot-room write p99 500ms 미만, partition pruning 증거, replica lag p95 2초 미만, relation/index size 50GB 이하, 테이블별 vacuum 비감소 및 dead/live 10% 이하이다. 이번 run의 미실행 항목은 없다. 운영 스키마 마이그레이션·자동 shard cutover는 C4 범위 밖이므로 별도 task로 남긴다.
