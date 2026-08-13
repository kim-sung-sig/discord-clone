# T171-C4 채팅 중심 PostgreSQL 확장성 실측 설계

## 문서 상태

- 상태: 설계안
- 기준 브랜치: `task_T171-C4-chat-scale`
- 기준 main: `9ea13919b15a270e10842970cdc524517fdb110a`
- 작성일: 2026-08-13
- 문서 언어: 한국어

## 1. 목표와 범위

T171-C4는 단일 리전 PostgreSQL에서 채팅 저장·조회가 어느 지점에서 파티션, read replica, hot-room shard를 필요로 하는지 재현 가능한 방법으로 측정한다.

이번 task는 **실측 harness와 의사결정 계약**을 구현한다. 운영 `messages` 테이블에 즉시 파티션을 적용하거나 실제 shard를 분리하지 않는다. 측정 결과가 승인 기준을 충족할 때만 별도 migration/운영 전환 task를 만든다.

범위:

- `event_date` UTC RANGE 파티션 후보
- `chat_room_id` HASH 16 bucket subpartition 후보
- primary/standby PostgreSQL의 history read 경로와 lag fallback 측정
- 채팅 write, cursor history read, read projection search 부하
- hot-room 분리 판정
- SQL plan, latency, error, WAL, lock, vacuum, replica lag 증거 저장

제외:

- 다중 리전·글로벌 DB
- Kafka fan-out 자체의 처리량 측정
- C3 Gateway durable cursor 구현 변경
- 실제 production shard migration·backfill·cutover
- 새로운 ORM·benchmark dependency 추가

## 2. 현재 코드와 호환 계약

현재 기준 스키마의 `messages`는 `channel_id`를 사용하고 `chat_room_id`는 아직 없다. C4 harness는 다음 호환 규칙을 고정한다.

```text
chat_room_id := channel_id
```

이 매핑은 측정용 alias이며 production domain 변경을 의미하지 않는다. 향후 Community가 별도 chat-room 식별자를 도입하면 harness 입력과 `ShardRoutingPolicy`의 key만 교체한다. 모든 측정 row에는 명시적인 `chat_room_id`와 UTC `event_date`를 함께 기록한다.

현재 운영 SQL migration은 변경하지 않는다. baseline은 현재 `messages`와 동일한 비파티션 구조를 복제하고, partition variant는 임시 benchmark schema에 별도 생성한다.

## 3. 비교군과 데이터 세트

모든 비교군은 같은 seed, row 수, SQL, connection pool, PostgreSQL image, hardware limit을 사용한다.

| Variant | 물리 구조 | 목적 |
|---|---|---|
| `baseline` | 비파티션 `messages` 유사 테이블 | 현재 구조 기준선 |
| `date_range` | `event_date` 기준 UTC 일자 RANGE | 시간 범위 pruning과 retention 측정 |
| `date_hash` | `event_date` RANGE 아래 `chat_room_id` HASH 16 bucket | hot-room 분산 후보 측정 |

기본 seed 값:

- 기간: UTC 30일
- 총 message row: 1,000,000
- chat room: 1,000개
- 일반 room: 전체 write의 80%
- hot-room 집합: room 1%가 전체 write의 20%
- 평균 content 크기: 1 KiB, 최대 2 KiB
- 각 room의 sequence는 `(chat_room_id, sequence)` 단조 증가
- 동일 idempotency key 재시도 샘플을 전체 write의 1% 포함

모든 값은 환경 변수로 조정할 수 있으나 결과 artifact에 실제 값을 기록한다. 1,000개 room과 1,000,000 row는 기본 재현 세트이며, 50GB 임계치 판정은 실제 `pg_total_relation_size`와 row 크기 관측값을 함께 기록해 별도 extrapolation으로 표시한다. extrapolation을 실측 결과로 위장하지 않는다.

## 4. 부하 시나리오

각 variant는 동일한 순서로 실행한다.

1. `ramp`: 5분, connection을 목표치까지 선형 증가
2. `steady`: 15분, 일반 room 80%와 hot-room 20%
3. `hot-room`: 15분, 단일 hot room write 집중
4. `recovery`: 5분, hot-room 부하 제거 후 p99와 lag 회복 관찰

기본 operation 비율:

- write 50%
- history cursor read 35%
- read projection search 15%

각 phase의 총 시간은 위의 5/15/15/5분을 유지하며 세 operation은 write 50%·history 35%·search 15%의 시간 창으로 순차 실행한다. 따라서 `-Variant all -DurationMinutes 40`은 variant 하나당 40분, 세 variant 전체 약 120분이 걸린다. 러너는 이 배분을 `operationSeconds`로 고정한다.

history read는 `before_cursor`와 limit 50을 사용하고 offset pagination은 사용하지 않는다. search는 message body를 반환하지 않고 count·latency만 기록한다.

실행 도구는 PostgreSQL image에 포함된 `pgbench`와 `psql`만 사용한다. 별도 Java benchmark framework나 npm dependency를 추가하지 않는다.

## 5. PostgreSQL primary/replica harness

`qa/c4-chat-scale/docker-compose.yml`에 임시 `primary`와 `replica`를 둔다.

- PostgreSQL 16 고정 image
- primary: `wal_level=replica`, replication slot, benchmark database
- replica: primary base backup으로 초기화하고 `primary_conninfo`로 streaming
- primary port: `15442`
- replica port: `15443`
- benchmark network는 로컬 전용이며 외부 공개하지 않는다.
- 각 run 시작 시 database/schema를 새로 만들고 종료 시 compose down으로 제거한다.

Replica lag 실측은 standby에서 `pg_wal_replay_pause()`를 사용해 의도적으로 replay를 멈춘 뒤 재개한다. 이 drill은 데이터 손실을 유발하지 않으며, lag 측정과 read fallback 계약만 검증한다.

## 6. read routing 계약

C4는 production service routing을 바로 변경하지 않고 다음 정책을 executable contract로 고정한다.

| 요청 유형 | 기본 대상 | fallback |
|---|---|---|
| write | primary | 실패 반환, replica write 금지 |
| read-after-write | primary | 없음; stale read 금지 |
| history/search | replica | lag > 2초면 primary |
| history/search, lag > 30초 | primary 제한 경로 | 신규 history page 제한 + 경고 |

artifact에는 요청 시각, replica replay lag, 선택된 대상(`primary`/`replica`), fallback 이유를 남긴다. read-after-write 판정은 write transaction의 commit timestamp와 query 시작 시각으로 확인한다.

## 7. 측정 항목과 증거 형식

각 run은 `qa/artifacts/c4-chat-scale/<run-id>/`에 다음 파일을 만든다. 이 디렉터리는 git에 커밋하지 않는다.

- `run.json`: commit SHA, Docker image, OS, seed, variant, duration, env
- `latency.tsv`: operation, count, error, p50, p95, p99, max
- `db-stats.tsv`: CPU, memory, WAL bytes, relation size, index size, lock wait, autovacuum state
- `replica-lag.tsv`: sample time, replay LSN, receive LSN, lag seconds, route decision
- `plans/*.txt`: `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)` 결과
- `decision.json`: variant별 판정과 근거 threshold

민감정보를 artifact에 저장하지 않는다. DSN password, token, message body, cookie, raw connection string은 제거하고 host/port와 metric만 기록한다.

## 8. 판정 규칙

### 8.1 공통 실패

다음 중 하나면 해당 run은 `invalid`이며 variant 채택 근거로 사용하지 않는다.

- error rate > 1%
- cursor 결과 중복·누락
- idempotency 재시도 sequence 불일치
- UTC 날짜 경계 row가 잘못된 partition에 저장됨
- 대상 partition 외 scan이 발생하고 pruning 증거가 없음

### 8.2 Partition 판정

- baseline이 모든 steady/hot-room 구간에서 `write p99 < 500ms`이고 relation size·vacuum도 안정적이면 partition 도입은 보류한다. 안정성은 `db-stats.tsv`의 모든 행이 유효하고, `relation_size_bytes`·`index_size_bytes`가 각각 50GB 이하이며, `vacuum` 값이 시간순으로 감소하지 않고, `live > 0`일 때 `dead/live <= 10%`인 경우로 고정한다. 행 누락·파싱 실패·임계 초과는 `INVALID`로 처리하며 p99만으로 `DEFER`하지 않는다.
- baseline이 `write p99 >= 500ms` 또는 partition size 50GB 이상을 15분 지속하면 `date_range`를 후보로 올린다.
- `date_range`가 history query pruning과 retention drop을 충족하고 baseline 대비 write/read p99가 악화되지 않으면 date partition을 권고한다.
- `date_hash`는 `date_range`에서 hot-room write QPS 1,000 이상 또는 write p99 500ms 이상이 15분 지속되고, `date_hash`가 hot-room p99를 20% 이상 줄이면서 error/replica lag을 10% 이상 악화시키지 않을 때만 권고한다.

### 8.3 Replica 판정

- steady 동안 lag p95 < 2초이고 history/search p99가 primary 대비 20% 이내면 replica read를 승인한다.
- lag > 2초 drill에서 primary fallback이 100% 발생하고 stale read가 0건이어야 한다.
- lag > 30초 drill에서 history 제한과 경고가 모두 발생해야 한다.

판정 결과는 `ADOPT`, `DEFER`, `INVALID` 중 하나이며, 샤딩을 자동으로 활성화하지 않는다.

## 9. 검증 명령 계약

기본 정적 계약:

```powershell
pwsh qa/c4-chat-scale.contract.ps1
```

Docker 실측:

```powershell
pwsh qa/c4-chat-scale/run.ps1 -Variant all -DurationMinutes 40
```

결과 검증:

```powershell
pwsh qa/c4-chat-scale/verify.ps1 -ArtifactDir qa/artifacts/c4-chat-scale/<run-id>
```

검증기는 명령 문자열을 외부 입력으로 실행하지 않고, 고정된 내부 명령과 artifact schema만 사용한다. 입력 DSN은 password marker가 포함되면 즉시 거부한다.

## 10. 향후 구현 경계

C4 구현 task는 다음만 추가한다.

- 임시 benchmark schema SQL
- pgbench script와 Docker primary/replica harness
- artifact writer와 판정 verifier
- `ShardRoutingPolicy(chatRoomId,eventDate) -> shardId`의 pure contract test 및 초기 `shard-0` 기록
- 설계·실측 결과 보고서

다음은 C4 결과 승인 후 별도 task다.

- production `messages` partition migration
- backfill·dual-write·cutover
- 실제 multi-shard routing과 신규 room migration
- application read/write datasource routing 변경

## 11. 완료 기준

- 세 variant가 같은 seed로 재현된다.
- primary/replica streaming과 lag fallback drill이 성공한다.
- latency, WAL, size, pruning, lag artifact가 schema대로 생성된다.
- 판정 verifier가 acceptance threshold를 자동 평가한다.
- 독립 spec/quality/security review 각 90점 이상, P0/P1 0
- Docker 실측 결과와 잔여 위험을 한국어 보고서에 기록한다.
