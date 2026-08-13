# T171-C4 채팅 중심 PostgreSQL 확장성 검증

현재 검증기는 `qa/c4-chat-scale/verify.ps1`가 생성한 `decision.json`을 기준으로 동작한다. baseline 1분 smoke(`qa/artifacts/c4-chat-scale/20260813-135513-641`)는 오류율 0%, p99 11.001ms, replica lag 최대 0초, cursor 중복 0을 기록했다.

독립 최종 리뷰는 9.5/10, P0/P1 0건으로 구현·검증기 승인을 완료했다. 검증기는 unknown variant, 누락/비수치 lag, NaN·Infinity·음수 수치, percentile 순서, `error_rate = error_count / count`, 9개 plan evidence, 3개 variant cursor evidence를 모두 fail-closed로 검사한다. 운영 판정은 40분 실측이 없으면 의도적으로 `REJECT:NOT_RUN`이다.

러너는 각 phase의 총 시간을 operation별로 write 50%·history 35%·search 15%로 분배한다. 이전 구현처럼 operation마다 phase 시간을 반복하지 않으므로 `all + 40`은 variant당 40분(전체 약 120분)이다.

## 40분 통합 실측 결과

아티팩트: `qa/artifacts/c4-chat-scale/20260813-141716-555` (36개 latency 행, variant별 12행, plan 9개, cursor gap 각 0, replica lag p95 9.358ms).

| variant | 최대 p99 | 해당 구간 | 판정 |
|---|---:|---|---|
| baseline | 543.075ms | recovery/write | 기준 초과 |
| date_range | 531.052ms | hot-room/write | 기준 초과 |
| date_hash | 241.187ms | recovery/write | 기준 이내 |

최종 verifier 결정은 `REJECT`이며 사유는 `baseline/write`, `date_range/write`의 p99 500ms 초과다. 오류율은 전 구간 0%, cursor 중복은 0, replica lag는 기준 이내였다. 따라서 현재 결과로는 운영 채택·PR 머지를 승인하지 않고, hot-room 쓰기 경로의 추가 최적화 또는 트래픽 분산 후 동일 40분 실측을 재실행해야 한다.

실행 명령:

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

판정 기준은 오류율 1% 미만, p99 500ms 미만, partition pruning 증거, replica lag p95 2초 미만이다. 미실행 항목은 `NOT_RUN`으로 남긴다.
