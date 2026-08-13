# T171-C4 채팅 중심 PostgreSQL 확장성 검증

현재 검증기는 `qa/c4-chat-scale/verify.ps1`가 생성한 `decision.json`을 기준으로 동작한다. 2026-08-13 기준으로는 baseline 1분 smoke만 완료했으며, 세 variant 40분 실측 전에는 파티션·replica 채택을 확정하지 않는다.

실행 명령:

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

판정 기준은 오류율 1% 미만, p99 500ms 미만, partition pruning 증거, replica lag p95 2초 미만이다. 미실행 항목은 `NOT_RUN`으로 남긴다.
