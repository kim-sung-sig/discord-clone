# C4 채팅 확장성 실측 실행기

계약과 1분 smoke:

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant baseline -DurationMinutes 1
```

```powershell
pwsh -NoProfile -File qa/c4-chat-scale.contract.ps1
pwsh -NoProfile -File qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
pwsh -NoProfile -File qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

표준 실행은 `-Variant all -DurationMinutes 40`이며 PostgreSQL 16 primary(15442)와 replica(15443)를 Docker Compose로 기동한다. 기본 seed는 1714이고, 40분 실행은 ramp 5분·steady 15분·hot-room 15분·recovery 5분으로 구성된다. 모든 단계는 `pgbench -j 4 -c 16 --aggregate-interval=10`을 사용한다.

로컬 Docker 메모리 편차를 줄이기 위해 Compose는 `shared_buffers=32MB`, `max_connections=32`, `work_mem=1MB`를 고정한다. 이는 운영 PostgreSQL 설정이 아닌 통제된 벤치마크 설정이다.

결과는 `qa/artifacts/c4-chat-scale/<run-id>/` 아래의 `run.json`, `latency.tsv`, `db-stats.tsv`, `replica-lag.tsv`, `routing.tsv`, `plans/`에만 기록된다. `run.json`에는 variant, seed, duration, UTC, git SHA만 저장하며 password·DSN·token·raw body는 저장하지 않는다. 실행 종료 시 compose는 `down -v --remove-orphans`로 정리된다.

Replica replay pause drill은 `run.ps1` 종료 단계에서 자동 실행된다. 결과는 `routing.tsv`에서 healthy history→replica, lag >2초→primary fallback, lag >30초→primary 제한 경고 정책으로 확인한다. 수동 재현이 필요하면 `docker compose -f qa/c4-chat-scale/docker-compose.yml exec -T replica psql -U c4_user -d c4chat -c "SELECT pg_wal_replay_pause();"`를 실행하고, 재개는 `SELECT pg_wal_replay_resume();`를 사용한다.
