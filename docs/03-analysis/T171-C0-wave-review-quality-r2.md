# T171-C0 Wave Quality/Security 재검수 r2

STATUS: PASS_WITH_P2_EVIDENCE_HYGIENE
SCORE: 94/100
FINDINGS: P0 0건, P1 0건, P2 1건
DECISION: pass / APPROVE
TEST_EVIDENCE: 제품 테스트 미실행, `rg` 기반 문서/코드 정적 대조만 수행

## 1. 결론

최종 판정은 **pass**다. 기준은 90점 이상 및 P0/P1 0건이고, 이번 재검수 결과는 94점, P0 0건, P1 0건, P2 1건이다.

보정 ADR은 C1 JWT verifier/refresh/revoke와 C3 outbox/Kafka adapter/inbox 결함을 "이미 구현됨"으로 허위 선언하지 않고, 구현 가능한 acceptance gate와 C0 승인 보류 조건으로 고정한다. command injection 위험이나 허위 테스트 증거는 확인되지 않았다.

## 2. 고정 입력

| 입력 | SHA-256 |
|---|---|
| `docs/02-design/features/T171-C0-msa-target-state.adr.md` | `BA52AD5868DB17BB89B17CCF4DC123F8ACE0668434190F89E71351E2FF1D6DC8` |
| `docs/03-analysis/T171-C0-wave-review-spec-r2.md` | `976D6EC78299CF19689AC901E3AD6099CB63877B92F638429F9EF5664F6A4283` |

작업 상태:

- branch: `task_T171c-msa-scaleout-decision`
- HEAD: `fe6807d17b9266e87c74c4e67f0ec9b1d7e95785`는 r2 spec에 기록된 기준 commit이다: `docs/03-analysis/T171-C0-wave-review-spec-r2.md:7`.
- `git status --short`에서 ADR/spec/quality-r2 파일은 untracked로 확인됐다.

## 3. Findings

### P0

없음.

### P1

없음.

### P2

1. **증거 재현성 위생**: `docs/03-analysis/T171-C0-wave-review-spec-r2.md:7`은 commit 기준을 적지만, 이번 작업 중 `git status --short`에서 `docs/02-design/features/T171-C0-msa-target-state.adr.md`, `docs/03-analysis/T171-C0-wave-review-spec-r2.md`, `docs/03-analysis/T171-C0-wave-review-quality-r2.md`가 untracked로 확인됐다. SHA-256으로 입력 내용은 고정되어 있어 pass를 막지는 않지만, 최종 handoff 전에는 "HEAD + SHA-256 고정 working tree 입력"으로 표현하거나 해당 파일을 commit에 포함해야 한다.

## 4. 코드 존재 대조

### C1 JWT verifier / refresh / revoke

- JWT verifier는 standalone 서비스에 실제로 존재한다: `backend/services/community/src/main/java/com/example/discord/communityservice/CommunityServiceApplication.java:24`, `backend/services/websocket/src/main/java/com/example/discord/websocketservice/WebsocketServiceApplication.java:24`, `backend/modules/identity/src/main/java/com/example/discord/identity/BearerTokenVerifier.java:8-12`.
- 현재 access token은 `kid/sub/iss/aud/iat/exp` 중심이고 `sid`, `authzVersion` 보존 타입이 없다: `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenService.java:33`, `backend/modules/identity/src/main/java/com/example/discord/identity/AccessTokenClaims.java:6`.
- 현재 독립 identity service TTL은 1시간이다: `backend/services/identity/src/main/java/com/example/discord/identityservice/IdentityServiceApplication.java:37`.
- refresh session 저장소와 revoke path는 실제 코드에 있다: `backend/boot/src/main/resources/db/migration/V6__auth_refresh_sessions.sql:1`, `backend/boot/src/main/java/com/example/discord/auth/JdbcAuthStore.java:202`, `backend/boot/src/main/java/com/example/discord/auth/JdbcAuthStore.java:220`, `backend/boot/src/main/java/com/example/discord/auth/AuthService.java:111`, `backend/boot/src/main/java/com/example/discord/auth/AuthService.java:141`.
- `rg -n "spring-session|SessionRepository|RedisIndexedSessionRepository" backend`는 hit가 없어 Spring Session Redis 배제는 현재 코드와 맞다.
- ADR acceptance는 이 gap을 구현 가능하게 닫는다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:150-151`이 `sid/authzVersion`, `exp-iat <= 900s`, refresh/revoke/reuse, legacy 종료, key rotation/reload 검증을 요구하고, 증거 없이는 C1 완료나 legacy 종료를 선언하지 못하게 한다.

### C3 outbox / Kafka adapter / inbox

- Message outbox는 실제로 있다: `backend/boot/src/main/resources/db/migration/V9__message_clean_persistence_ports.sql:30`, `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java:86`, `backend/modules/message/src/main/java/com/example/discord/message/MessagePublished.java:8`.
- 현재 Gateway command에는 원본 `eventId` 필드가 없다: `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayBusPublishCommand.java:7-8`.
- 현재 Kafka Gateway adapter는 publish 시 새 UUID를 만들고 `kafka.send(...)` future를 기다리지 않는다: `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:69`, `backend/boot/src/main/java/com/example/discord/gateway/KafkaGatewayEventBus.java:76`.
- 현재 Redis Gateway adapter도 새 UUID를 만든다: `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java:102`.
- 현재 message relay는 dispatcher 반환 직후 published 처리한다: `backend/modules/message/src/main/java/com/example/discord/message/DefaultMessagePublicationRelay.java:67`.
- `rg -n "consumer_inbox|gateway_consumer_inbox" backend/boot/src/main/resources/db/migration backend/services backend/modules`는 hit가 없어 Gateway consumer inbox DDL/구현 부재가 실제 gap으로 확인된다.
- ADR acceptance는 이 gap을 C0 승인 전 차단한다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:205`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:211`, `docs/02-design/features/T171-C0-msa-target-state.adr.md:214`.

## 5. Gate 판단

- Acceptance 구현 가능성: pass. ADR은 구현 완료를 주장하지 않고, 현재 gap 재현과 목표 해소 증거를 C1/C3 필수 산출물로 요구한다.
- C0 승인 오인 방지: pass. `docs/02-design/features/T171-C0-msa-target-state.adr.md:319`는 C1 legacy/revoke gap과 C3 eventId/ACK/inbox gap의 재현 및 해소 증거가 빠지면 C0 승인을 보류한다고 고정한다.
- 허위 테스트 증거: pass. r2 spec은 제품 테스트를 실행하지 않았다고 명시한다: `docs/03-analysis/T171-C0-wave-review-spec-r2.md:105`.
- command injection: pass. 문서 내 실행 명령은 고정된 `./gradlew` 및 `rg` 조회이며 사용자 입력을 shell command에 삽입하는 형태가 아니다: `docs/02-design/features/T171-C0-msa-target-state.adr.md:151`, `docs/03-analysis/T171-C0-wave-review-spec-r2.md:96-103`.

## 6. 실행한 검증

제품 테스트: **미실행**. 시간 제한과 지시에 따라 `rg` 기반 정적 존재 확인만 수행했다.

사용한 주요 명령:

- `git status --short`
- `git branch --show-current`
- `rg -n "BearerTokenVerifier|AccessTokenService|AccessTokenClaims|Duration\\.ofHours\\(1\\)|sid|authzVersion|kid|SessionRepository|spring-session|auth_refresh_sessions|refresh|revoke|revoked" backend ...`
- `rg -n "DefaultPublishMessageUseCase|DefaultMessagePublicationRelay|MessagePublished|message_publication_outbox|outbox|GatewayBusPublishCommand|KafkaGatewayEventBus|RedisGatewayEventBus|kafka\\.send|markPublished|consumer_inbox|inbox" backend ...`
- `rg -n "consumer_inbox|gateway_consumer_inbox|notification_inbox|message_publication_outbox" backend/boot/src/main/resources/db/migration backend/services backend/modules ...`

`sha256sum`, `Get-FileHash`, `git rev-parse HEAD`는 현재 샌드박스에서 `CreateProcessAsUserW failed: 5`로 실행되지 않았다. SHA-256 값은 기존 r2 품질 파일에 기록된 값을 유지했고, 코드/문서 판정은 이번 `rg` 재검수 출력으로 다시 대조했다.

## 7. 최종 판정

**pass / APPROVE**.

P2 증거 위생 이슈는 남지만, 구현 acceptance gate는 실제 코드 gap을 닫을 수 있는 테스트·설정·DDL·로그 기준으로 충분히 구체적이다. P0/P1이 없고 94점이므로 기준을 만족한다.
