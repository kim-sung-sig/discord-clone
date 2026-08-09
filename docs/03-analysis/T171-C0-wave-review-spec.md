# T171-C0 First Discovery Wave Spec Review

## 결론

- 판정: pass
- 점수: 92 / 100
- 통과 기준: 90점 이상, P0/P1 0건
- P0: 0
- P1: 0
- P2: 3

## 고정 입력

- 대상 문서: `docs/02-design/features/T171-C0-msa-target-state.adr.md`
- SHA-256: `D559AE6B5791A212351141D2480C12C71105DE59DC1B5AF41637BA74BC30DA70`
- 산출 방식: `Get-FileHash -Algorithm SHA256 -Path docs\02-design\features\T171-C0-msa-target-state.adr.md`
- 검수 범위: C0 target-state ADR이 사용자 확정 조건과 현재 코드 근거를 충분히 반영하는지 독립 검수했다. 코드와 기존 문서는 수정하지 않았다.

## 통과 근거

1. 단일 리전 우선 조건을 반영했다. ADR은 단일 리전 운영을 범위로 고정하고 active-active/cross-region failover를 제외한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:15`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:237`.
2. 서비스별 DB 소유와 cross-DB 금지를 명확히 했다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:10`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:111`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:122`.
3. 강결합 범위를 authz/command/idempotency 쪽으로 제한하고, 기본 상태 전파를 outbox/Kafka/inbox dedup으로 둔다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:11`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:157`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:166`.
4. 권한 원본과 서비스별 projection을 분리했다. Community가 RBAC 원본이고 각 서비스가 자기 DB projection으로 판정한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:13`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:148`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:150`.
5. JWT/refresh 근거는 현재 코드와 대체로 일치한다. Identity service만 private key location을 요구한다: `backend/services/identity/src/main/java/com/example/discord/identityservice/IdentityServiceApplication.java:37`, `backend/services/identity/src/main/java/com/example/discord/identityservice/IdentityServiceApplication.java:40`. Message/Community/WebSocket 서비스는 public key map 기반 verifier만 구성한다: `backend/services/message/src/main/java/com/example/discord/messageservice/MessageServiceApplication.java:24`, `backend/services/community/src/main/java/com/example/discord/communityservice/CommunityServiceApplication.java:24`, `backend/services/websocket/src/main/java/com/example/discord/websocketservice/WebsocketServiceApplication.java:24`.
6. refresh session은 DB-backed이다. 스키마가 `auth_refresh_sessions`를 만들고 token hash만 저장한다: `backend/boot/src/main/resources/db/migration/V6__auth_refresh_sessions.sql:1`, `backend/boot/src/main/resources/db/migration/V6__auth_refresh_sessions.sql:4`. 재사용 감지 시 session family revoke도 코드에 있다: `backend/boot/src/main/java/com/example/discord/auth/AuthService.java:110`, `backend/boot/src/main/java/com/example/discord/auth/AuthService.java:111`.
7. Spring Session Redis를 도입하지 않는 판단은 근거가 있다. 검수 중 `spring-session`, `SessionRepository`, `RedisIndexedSessionRepository`, `springframework.session` 검색 결과는 ADR/분석 문서 외 product code와 build 파일에서 발견되지 않았다.
8. Message publish 경로는 idempotency와 outbox 생성을 같은 publish use case에 묶고 있다: `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java:45`, `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java:95`. DB 스키마도 idempotency key와 publication outbox를 가진다: `backend/boot/src/main/resources/db/migration/V9__message_clean_persistence_ports.sql:14`, `backend/boot/src/main/resources/db/migration/V9__message_clean_persistence_ports.sql:30`.
9. Gateway control과 WebSocket transport 분리 방향은 현재 상태와 목표 상태를 구분해 썼다. 현재 boot WS handler는 socket을 직접 들고 gateway service를 호출한다: `backend/boot/src/main/java/com/example/discord/gateway/GatewayWebSocketHandler.java:24`, `backend/boot/src/main/java/com/example/discord/gateway/GatewayWebSocketHandler.java:30`, `backend/boot/src/main/java/com/example/discord/gateway/GatewayWebSocketHandler.java:82`. ADR은 최종 책임에서 Gateway Control의 socket 소유를 금지하고 WebSocket Transport의 durable sequence 생성을 금지한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:40`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:41`.

## 결함 목록

### P2-1: 최종 JWT claim과 현재 JWT 구현 차이가 C1 추적 항목으로 더 선명해야 한다

- 근거: ADR은 최종 access JWT에 `sid`와 `authzVersion`을 포함한다고 한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:141`.
- 근거: 현재 `AccessTokenService.issue`는 subject, issuer, audience, iat, exp, kid만 발급한다: `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:33`.
- 근거: 현재 verifier 반환 claim도 userId/issuedAt/expiresAt뿐이다: `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:43`.
- 영향: ADR이 `legacy` audience 전환을 언급해 차단 결함은 아니지만, C1 acceptance test가 누락되면 target claim이 문서에만 남을 수 있다.
- 권고: C1 작업에는 `sid`, `authzVersion`, 15분 TTL, legacy 허용 종료 조건을 별도 acceptance test로 고정한다.

### P2-2: message partition의 `16 bucket` 기본값은 실측 전 확정처럼 읽힐 수 있다

- 근거: ADR은 shard 수와 routing key를 측정 결과로만 늘린다고 한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:14`.
- 근거: 동시에 초기 physical layout을 `chat_room_bucket_00..15`로 적는다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:127`.
- 근거: 그 다음 줄에서 초기 shard 0 하나와 16 bucket 계산 결과만 기록한다고 완화한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:128`.
- 영향: 사용자 확정 조건의 "실측 전 확정 금지"와 충돌하지 않으려면 bucket 수가 shard 확정값이 아니라 초기 partition-local bucket 관측값임을 구현 태스크에서 재확인해야 한다.
- 권고: C6 acceptance에 "기존 bucket 재배치 금지, 새 shard는 30일 부하 검증 후"를 필수 조건으로 둔다.

### P2-3: Gateway durable cursor 목표는 현재 구현과 간극이 커서 C3/C4 증명이 필요하다

- 근거: 현재 gateway session은 `lastDeliveredSequence`를 가진다: `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewaySession.java:14`.
- 근거: 현재 `InMemoryGatewayService`는 `events`와 `nextSequence`를 메모리에 둔다: `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java:24`, `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java:27`.
- 근거: ADR은 최종적으로 Gateway DB event log cursor와 ACK/resume protocol을 둔다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:40`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:235`.
- 영향: 현재 코드 근거는 target state의 방향을 뒷받침하지만 durable delivery 완료 증거는 아니다.
- 권고: C3에는 reconnect/resume/ACK/retention 장애 drill을 구현 전 acceptance로 고정한다. ADR의 전환 표도 이를 요구한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:290`.

## 최종 판정

pass. C0 ADR은 사용자 확정 조건을 충족하며, P0/P1 없이 구현 착수 전 계약 문서로 사용할 수 있다. 단, 위 P2 3건은 C1/C3/C6 작업의 acceptance test로 승격해야 한다.
