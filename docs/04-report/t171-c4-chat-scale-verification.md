# T171-C4 채팅 중심 PostgreSQL 확장성 검증

현재 검증기는 `qa/c4-chat-scale/verify.ps1`가 생성한 `decision.json`을 기준으로 동작한다. baseline 1분 smoke(`qa/artifacts/c4-chat-scale/20260813-135513-641`)는 오류율 0%, p99 11.001ms, replica lag 최대 0초, cursor 중복 0을 기록했다.

독립 최종 리뷰는 관계 크기·vacuum 안정성 게이트를 추가하기 전 기준으로 9.5/10, P0/P1 0건이었다. 현재 검증기는 unknown variant, 누락/비수치 lag, NaN·Infinity·음수 수치, percentile 순서, `error_rate = error_count / count`, 9개 plan evidence, 3개 variant cursor evidence, relation/index size·vacuum 안정성을 모두 fail-closed로 검사한다. 운영 판정은 40분 실측이 없거나 새 `db-stats.tsv` 안정성 증거가 없으면 의도적으로 `REJECT:NOT_RUN`이다. 따라서 아래 40분 결과는 새 안정성 게이트를 적용하기 전 산출물이며, 코드 변경 후 재실측이 필요하다.

러너는 각 phase의 총 시간을 operation별로 write 50%·history 35%·search 15%로 분배한다. 이전 구현처럼 operation마다 phase 시간을 반복하지 않으므로 `all + 40`은 variant당 40분(전체 약 120분)이다.

## 40분 통합 실측 결과

아티팩트: `qa/artifacts/c4-chat-scale/20260813-141716-555` (36개 latency 행, variant별 12행, plan 9개, cursor gap 각 0, replica lag p95 9.358ms). 이 아티팩트의 `db-stats.tsv`는 새 relation/index size 열이 없어 현재 검증기에서 운영 근거로 재사용하지 않는다.

| variant | 최대 p99 | 해당 구간 | 판정 |
|---|---:|---|---|
| baseline | 543.075ms | recovery/write | 기준 초과 |
| date_range | 531.052ms | hot-room/write | 기준 초과 |
| date_hash | 241.187ms | recovery/write | 기준 이내 |

전체 후보의 raw 최대 p99에는 baseline/date_range의 recovery 또는 hot-room 초과가 있지만, partition 채택 기준은 baseline의 steady·hot-room write p99다. baseline은 steady 381.989ms, hot-room 471.758ms로 모두 500ms 미만이므로 최종 결정은 `ACCEPT`, 후보 결정은 `DEFER`, 선택 variant는 `baseline`이다. date_hash의 hot-room 219.092ms 개선은 기록하되, baseline 보류 조건을 만족하므로 운영 partition으로 채택하지 않는다. recovery/write p99 543.075ms는 별도 회복성 최적화 과제로 남긴다.

실행 명령:

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

판정 기준은 오류율 1% 미만, p99 500ms 미만, partition pruning 증거, replica lag p95 2초 미만이다. 미실행 항목은 `NOT_RUN`으로 남긴다.
