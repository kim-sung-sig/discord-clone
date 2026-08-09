# T171-C0 Wave 통합 계약 검수

검수 결론: **reject**. 세 문서는 C1/C3의 실측 결과를 대체로 정확히 반영하지만, C0 ADR이 C3의 현재 구현 결함을 “구현 전제/acceptance gate”로 충분히 고정하지 못해 C0 승인 기준인 90점 이상 및 P0/P1 0개를 만족하지 못한다.

## 1. 검수 대상과 고정 해시

해시는 `Get-FileHash -Algorithm SHA256`로 산출했다.

| 파일 | SHA-256 |
|---|---|
| `docs/02-design/features/T171-C0-msa-target-state.adr.md` | `D559AE6B5791A212351141D2480C12C71105DE59DC1B5AF41637BA74BC30DA70` |
| `docs/03-analysis/T171-C1-auth-session-discovery.md` | `F01FFB29DE532D2BD601B11F6B60B6B1AD361495C62CDA521ABD536008E7DEB4` |
| `docs/03-analysis/T171-C3-event-contract-discovery.md` | `9A87FA07D03F752BECDEC7533FF39E9F312464AC2541D641886C581D4A034760` |

검수 기준:

- 통과: 점수 90 이상, P0/P1 0개
- 실제 소스와 문서 간 불일치, 구현 전 계약 공백, 통과 기준 오인 가능성을 우선 결함으로 본다.

## 2. 점수

| 항목 | 점수 | 판단 |
|---|---:|---|
| C1 인증·세션 실측 정확도 | 92 | Spring Session 부재, DB refresh session, 현재 JWT claim, legacy-auth 범위가 소스와 맞다. |
| C3 이벤트·Outbox 실측 정확도 | 91 | eventId 단절, Kafka send future 미대기, consumer inbox 부재, message-service wiring 부재가 소스와 맞다. |
| C0 ADR의 C1/C3 통합 계약성 | 78 | 방향은 맞지만 C3의 P1 구현 전제와 C1 전환 검증을 C0 승인 gate로 충분히 고정하지 못했다. |
| 종합 | 84 | P1 2개가 남아 reject. |

## 3. P0

없음.

## 4. P1

### P1-1. C3의 eventId 보존·Kafka ACK·consumer inbox 결함이 C0 승인 gate로 닫히지 않았다

영향: C0 ADR은 “eventId 불변·Outbox→Kafka→consumer inbox”를 최종 계약으로 선언하지만, 현재 소스는 그 계약을 실행할 수 없다. C0가 이 격차를 C3 구현 acceptance로만 일부 남기면 C0 승인 후 구현 착수자가 현재 Gateway bus를 재사용해도 계약 위반을 놓칠 수 있다.

근거:

- C0 ADR은 `eventId` 불변과 inbox dedup을 계약으로 둔다. `docs/02-design/features/T171-C0-msa-target-state.adr.md:190` “`eventId`는 원본 aggregate transaction에서 한 번 생성하고 Gateway event까지 변경하지 않는다.”, `:193` “Consumer는 `consumer_inbox(event_id, consumer_name)` unique 제약으로 중복을 제거...”
- C3 보고서는 현재 구현이 이 계약을 구현할 수 없다고 명시한다. `docs/03-analysis/T171-C3-event-contract-discovery.md:10` “`MessagePublished.eventId`가 Gateway 이벤트까지 전달되지 않고 Gateway bus가 새 ID를 생성...” `:183` “P1 | MessagePublished eventId가 Gateway에서 새 UUID로 교체됨”
- 실제 소스도 같다. `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java:86-94`에서 `MessagePublished`를 생성하지만, `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayBusPublishCommand.java:7-12`에는 원본 `eventId` 필드가 없다. `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:67-76`와 `RedisGatewayEventBus.java:100-111`은 publish 시 새 `UUID.randomUUID()`를 생성한다.
- broker ACK 전 published 방지 계약도 현재 구현과 분리되어 있다. `backend/modules/message/src/main/java/com/example/discord/message/DefaultMessagePublicationRelay.java:66-67`은 dispatcher 반환 직후 `markPublished`를 호출하고, `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:76`은 `kafka.send(...)` future를 기다리지 않는다.

최소 수정: C0 ADR의 C3 gate에 “현재 GatewayBusPublishCommand/KafkaGatewayEventBus/RedisGatewayEventBus 재사용 금지 또는 eventId-preserving v2 command로 교체”를 명시하고, Kafka ACK 성공 전 `published_at` 금지와 `(consumer_name,event_id)` inbox transaction을 C3-1 acceptance의 필수 선행 조건으로 올린다.

### P1-2. C1의 인증 전환 계약이 현재 JWT 1시간/무 sid·무 authzVersion 상태에서 목표 15분 claim으로 넘어가는 검증을 충분히 고정하지 않았다

영향: C0 ADR은 목표 JWT를 `sid`, `authzVersion`, TTL 15분으로 확정하지만, C1 보고서가 요구한 standalone revoke gap 재현과 claim migration 검증을 C0 승인 조건으로 묶지 않는다. 이 상태에서는 legacy boot와 독립 서비스 간 revoke 의미가 다른 채로 “Identity 인증 계약 승인”을 선언할 수 있다.

근거:

- C1 보고서는 현재 JWT가 `sid`/`authzVersion` 없이 발급된다고 한다. `docs/03-analysis/T171-C1-auth-session-discovery.md:19` “현재 JWT에는 `sub`, `iss`, `aud`, `iat`, `exp`, `kid`만 들어가며 `sid`와 `authzVersion`은 없다.” `:233`은 “standalone 서비스가 revoke된 legacy token을 허용하는 현재 gap” 재현 테스트를 요구한다.
- 실제 소스도 같다. `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:33`은 `kid/sub/iss/aud/iat/exp`만 빌드하고, `AccessTokenClaims.java:6`은 `userId`, `issuedAt`, `expiresAt`만 보존한다. `backend/services/identity/src/main/java/com/example/discord/identityservice/IdentityServiceApplication.java:37`은 TTL을 `Duration.ofHours(1)`로 둔다.
- C0 ADR은 목표 claim과 TTL을 확정한다. `docs/02-design/features/T171-C0-msa-target-state.adr.md:141` “`sid=UUID`, `authzVersion=long`... TTL 15분.” 하지만 C1의 필수 결정/검증 항목인 claim 타입·범위, standalone revoke gap, legacy 종료 검증은 C0 승인 gate에 직접 연결되어 있지 않다.

최소 수정: C0 ADR의 C1 gate에 `sid/authzVersion` claim migration test, 1시간 legacy token과 15분 target token의 병행 허용 범위, standalone 서비스 revoke gap 재현 및 목표 검증 방식을 필수 증거로 추가한다.

## 5. P2

### P2-1. C0 문서 상태와 승인 조건 표현이 혼재한다

근거: C0 ADR은 `docs/02-design/features/T171-C0-msa-target-state.adr.md:3`에서 “Draft (독립 Spec/Security/SRE 검수 전)”이라고 하면서, `:287`에서 C0 통과 기준을 “Spec/Security/SRE 각 90점 이상, P0/P1 0”으로 둔다. 이 통합 검수는 Spec/Security/SRE 3자 검수가 아니므로, 후속 문서에 “본 검수는 C0 승인 전 통합 계약 사전검수”라고 분리 표시하는 편이 안전하다.

### P2-2. C0의 Kubernetes/운영 수치 일부는 실측 근거보다 목표값에 가깝다

근거: C0 ADR은 Kafka 3 broker, Redis HA, PostgreSQL primary+2 replica, PDB/HPA 수치를 상세히 둔다. 목표 상태로는 가능하지만 C1/C3 입력 보고서의 직접 조사 범위는 인증·세션·RBAC와 이벤트/outbox/Kafka이며, 해당 운영 수치의 코드/런타임 근거는 이번 통합 입력만으로는 충분하지 않다. C0에는 “운영 토폴로지는 별도 SRE 검수 전 목표값”이라고 표시하는 것이 좋다.

## 6. Pass/Reject

**reject**

사유:

- 종합 84점으로 기준 90점 미만이다.
- P1 2개가 남아 있다.
- P0는 없지만, C0 승인 후 C3/C1 구현자가 잘못된 현재 adapter나 legacy revoke 의미를 그대로 가져갈 위험이 있다.

## 7. 반려 시 최소 수정 순서

1. C0 ADR의 C3 gate부터 수정한다. eventId-preserving command/envelope, Kafka ACK 후 published, consumer inbox transaction, message-service context wiring을 C3-1 선행 acceptance로 올린다.
2. C0 ADR의 C1 gate를 수정한다. `sid/authzVersion` claim migration, target TTL 15분 전환, legacy 1시간 token 허용 종료, standalone revoke gap 재현/해결 검증을 필수 증거로 둔다.
3. C0 상태 문구를 “Draft/사전 통합 검수 reject”로 유지하고, Spec/Security/SRE 검수와 본 통합 검수를 분리 표기한다.
4. 수정 후 세 파일의 SHA-256을 다시 고정하고 같은 기준(90점 이상, P0/P1 0)으로 재검수한다.

## 8. 검수에 사용한 실제 소스 근거 요약

- Spring Session 관련 검색: `rg -n 'spring-session|SessionRepository|RedisIndexedSessionRepository|HttpSession|SecurityFilterChain' backend -g '!**/build/**'` 결과 무매치.
- JWT 발급/검증: `AccessTokenService.java:33`, `AccessTokenService.java:42-43`, `AccessTokenClaims.java:6`, `IdentityServiceApplication.java:37`.
- legacy auth/revoke: `AuthController.java:33`, `AuthService.java:29`, `AuthenticatedUserResolver.java:22-26`, `AuthStore.java:18-20`.
- Message event 생성: `DefaultPublishMessageUseCase.java:86-94`, `MessagePublished.java:8-15`.
- Gateway eventId 단절: `GatewayBusPublishCommand.java:7-12`, `KafkaGatewayEventBus.java:67-76`, `RedisGatewayEventBus.java:100-111`.
- Outbox published timing: `DefaultMessagePublicationRelay.java:66-67`, `KafkaGatewayEventBus.java:76`.
