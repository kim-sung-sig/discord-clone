# T171-C0 Wave Spec 재검수 r2

검수 결론: **pass**. 보정된 C0 ADR은 이전 통합 P1 두 건을 C0 승인 gate와 C1/C3 acceptance로 직접 승격했으므로 기준인 90점 이상 및 P0/P1 0건을 만족한다.

## 1. 고정 입력

검수 기준 commit: `fe6807d17b9266e87c74c4e67f0ec9b1d7e95785`
branch: `task_T171c-msa-scaleout-decision`
검수 대상: `docs/02-design/features/T171-C0-msa-target-state.adr.md`

SHA-256 계산 명령:

```bash
sha256sum docs/02-design/features/T171-C0-msa-target-state.adr.md
```

| 입력 | SHA-256 |
|---|---|
| `docs/02-design/features/T171-C0-msa-target-state.adr.md` | `ba52ad5868db17bb89b17ccf4dc123f8ace0668434190f89e71351e2ff1d6dc8` |

참고 대조 입력:

| 참고 파일 | SHA-256 |
|---|---|
| `docs/03-analysis/T171-C1-auth-session-discovery.md` | `f01ffb29de532d2bd601b11f6b60b6b1ad361495c62cda521abd536008e7deb4` |
| `docs/03-analysis/T171-C3-event-contract-discovery.md` | `9a87fa07d03f752becdec7533ff39e9f312464ac2541d641886c581d4a034760` |
| `docs/03-analysis/T171-C0-wave-review-integration.md` | `7c4d52cd9ad880ec53e4000d912415766ecfb920d2c4d99bdfae6bd505856d53` |
| `docs/03-analysis/T171-C1-C3-wave-review-quality.md` | `d7f19ebb19b38d30c1bbfbac2b72261723b44c2cf8cb33be621732cae413e424` |

## 2. 점수와 판정

| 항목 | 점수 | P0 | P1 | P2 | 판정 |
|---|---:|---:|---:|---:|---|
| C1 JWT/session/revoke/key rotation gate 반영 | 95 | 0 | 0 | 0 | PASS |
| C3 eventId/Kafka ACK/inbox/DLQ gate 반영 | 96 | 0 | 0 | 0 | PASS |
| C0 승인 gate의 구현 전 오인 방지 | 94 | 0 | 0 | 0 | PASS |
| 종합 | 95 | 0 | 0 | 0 | PASS |

통과 기준: 점수 90 이상, P0/P1 0건.
최종 판정: **pass**.

## 3. Findings

### P0

없음.

### P1

없음.

이전 통합 검수의 P1-1, P1-2는 보정 ADR에서 닫혔다.

- C1 P1 해소: `docs/02-design/features/T171-C0-msa-target-state.adr.md:141-151`이 목표 JWT `sid`, `authzVersion`, 15분 TTL, session/account revoke, legacy 1시간 token 허용 범위, 99.9%/7일 종료, old/new `kid` overlap, unknown `kid` 거부, verifier reload, 최소 검증 명령을 C1 acceptance로 고정한다.
- C3 P1 해소: `docs/02-design/features/T171-C0-msa-target-state.adr.md:197-214`가 원본 `eventId` 보존, Kafka `acks=all`/idempotence/ACK 후 `published_at`, consumer inbox unique transaction, retry/DLQ, 기존 Gateway bus 재사용 금지, 독립 message service context 검증을 C3 acceptance로 고정한다.
- C0 승인 gate 해소: `docs/02-design/features/T171-C0-msa-target-state.adr.md:317-319`가 C1 legacy/revoke gap과 C3 eventId/ACK/inbox gap의 재현 및 해소 증거가 빠지면 C0 승인을 보류하도록 명시한다.

### P2

없음.

## 4. 핵심 대조 근거

### C1

보정 ADR은 현재 소스와 C1 보고서의 gap을 사실로 둔 뒤, 구현 acceptance로 닫는다.

- C1 보고서: `docs/03-analysis/T171-C1-auth-session-discovery.md:19`은 현재 JWT에 `sid`와 `authzVersion`이 없다고 기록한다.
- C1 보고서: `docs/03-analysis/T171-C1-auth-session-discovery.md:69-72`는 현재 TTL 1시간, `AccessTokenClaims` 보존값, key rotation orchestration 부재를 기록한다.
- C1 보고서: `docs/03-analysis/T171-C1-auth-session-discovery.md:105`는 standalone 서비스가 revoke된 legacy token을 만료 전 허용할 수 있는 P1 gap을 기록한다.
- C1 보고서: `docs/03-analysis/T171-C1-auth-session-discovery.md:231-233`은 unknown kid/expired token 및 standalone revoke gap 재현 테스트를 요구한다.
- 실제 소스: `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:33`은 `kid/sub/iss/aud/iat/exp`만 발급한다.
- 실제 소스: `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenClaims.java:6`은 `userId`, `issuedAt`, `expiresAt`만 보존한다.
- 실제 소스: `backend/services/identity/src/main/java/com/example/discord/identityservice/IdentityServiceApplication.java:37`은 현재 TTL을 `Duration.ofHours(1)`로 둔다.
- ADR 해소: `docs/02-design/features/T171-C0-msa-target-state.adr.md:148-151`이 `sid/authzVersion`, `exp-iat <= 900s`, refresh/revoke/reuse, legacy 종료, key rotation 검증을 C1 통과 필수 증거로 올린다.

### C3

보정 ADR은 현재 Gateway bus 재사용 위험을 C0 승인 전 차단 조건으로 올렸다.

- C3 보고서: `docs/03-analysis/T171-C3-event-contract-discovery.md:10`은 `MessagePublished.eventId`가 Gateway까지 전달되지 않고 Gateway bus가 새 ID를 만든다고 기록한다.
- C3 보고서: `docs/03-analysis/T171-C3-event-contract-discovery.md:115`는 dispatcher가 비동기 Kafka `send()` future를 기다리지 않으면 broker ACK 전 `published_at`이 채워질 수 있다고 기록한다.
- C3 보고서: `docs/03-analysis/T171-C3-event-contract-discovery.md:183-186`은 eventId 교체, Kafka ACK 미대기, message service wiring 부재, consumer inbox 부재를 P1로 분류한다.
- C3 보고서: `docs/03-analysis/T171-C3-event-contract-discovery.md:215-220`은 eventId 보존, ACK 후 publish, inbox/projection transaction을 최소 계약으로 둔다.
- 실제 소스: `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java:86-95`는 `MessagePublished.eventId`를 만들고 outbox 저장까지 넘긴다.
- 실제 소스: `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayBusPublishCommand.java:7-12`에는 원본 `eventId` 필드가 없다.
- 실제 소스: `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:67-76`은 새 UUID를 만들고 `kafka.send(...)` future를 기다리지 않는다.
- 실제 소스: `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java:100-111`도 새 UUID를 생성한다.
- 실제 소스: `backend/modules/message/src/main/java/com/example/discord/message/DefaultMessagePublicationRelay.java:65-67`은 dispatcher 반환 직후 `markPublished`를 호출한다.
- ADR 해소: `docs/02-design/features/T171-C0-msa-target-state.adr.md:205-214`가 기존 adapter 재사용 금지, eventId-preserving command/envelope, ACK 후 publish, inbox 원자성, retry/DLQ, 독립 message service wiring 검증을 C3 acceptance로 고정한다.

## 5. 잔여 위험

잔여 P0/P1/P2는 없다. 다만 이 pass는 **C0 ADR의 spec gate 통과**를 의미하며, C1/C3 구현 완료를 의미하지 않는다. ADR 자체도 `docs/02-design/features/T171-C0-msa-target-state.adr.md:349`에서 구현은 C1~C6 각 task의 독립 branch에서 acceptance test를 먼저 작성한 뒤 진행한다고 경계를 둔다.

## 6. 사용한 명령

- `sha256sum docs/02-design/features/T171-C0-msa-target-state.adr.md docs/03-analysis/T171-C1-auth-session-discovery.md docs/03-analysis/T171-C3-event-contract-discovery.md docs/03-analysis/T171-C0-wave-review-integration.md docs/03-analysis/T171-C1-C3-wave-review-quality.md`
- `nl -ba docs/02-design/features/T171-C0-msa-target-state.adr.md`
- `nl -ba docs/03-analysis/T171-C1-auth-session-discovery.md`
- `nl -ba docs/03-analysis/T171-C3-event-contract-discovery.md`
- `rg -n "Duration\\.ofHours\\(1\\)|sid|authzVersion|legacy|kid|class AccessTokenService|record AccessTokenClaims|BearerTokenVerifier|account_security|SessionRepository|spring-session" backend -g "*.java" -g "*.kt" -g "*.kts" -g "*.sql" -g "*.yml"`
- `rg -n "record MessagePublished|record GatewayBusPublishCommand|class KafkaGatewayEventBus|class RedisGatewayEventBus|UUID\\.randomUUID|kafka\\.send|markPublished|consumer_inbox|DefaultPublishMessageUseCase|DefaultMessagePublicationRelay" backend -g "*.java" -g "*.sql" -g "*.kts"`

테스트 실행: 없음. 이번 작업은 보정 ADR의 spec 재검수이며 제품 코드 동작 변경 검증이 아니다.
