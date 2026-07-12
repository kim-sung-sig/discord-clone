# T191 Kafka Gateway Confidentiality Boundary Decision Plan

Created: 2026-07-11
PDCA Phase: Plan
Slice: T191 Kafka Gateway Confidentiality Boundary Decision
Type: security
Priority: P0

## Executive Summary

| View | Content |
| --- | --- |
| Problem | `KafkaGatewayEventBus`가 Gateway event 전체 JSON을 Kafka broker와 복제본에 평문으로 저장한다. 현재 sanitizer는 token/secret류 제거만 보장하며, 개인·민감 payload 여부는 분류되지 않았다. |
| Solution | 실제 producer caller와 payload를 분류하고, 암호화 불필요/제한적 적용/봉투 암호화 중 하나를 승인 가능한 근거와 후속 구현 범위로 결정한다. |
| Function UX Effect | 민감 정보가 Kafka, DLQ, 로그, 백업으로 예상치 않게 확산되는 경로를 명시적으로 차단한다. |
| Core Value | 필요하지 않은 KMS·암호화 추상화는 만들지 않고, 필요한 경우에만 안전한 E2EE 도입 경계를 확정한다. |

## Scope

- `GatewayBusPublishCommand`의 모든 producer caller와 payload field를 `public/internal/confidential`로 분류한다.
- Kafka topic, consumer group, DLQ, metrics, logs, backup 및 운영자 접근을 포함한 위협 모델을 작성한다.
- 다음 셋 중 하나를 선택하고, 선택 근거와 잔여 위험을 기록한다.
  - 암호화 불필요: payload 계약과 sanitizer 보강만으로 충분하다.
  - 제한적 암호화: 특정 event type 또는 field만 별도 topic/암호화 경계로 이동한다.
  - 봉투 암호화: AES-GCM DEK와 KMS 관리 KEK를 사용해 Kafka record value를 암호화한다.
- 봉투 암호화를 선택할 경우 T192 구현 blueprint에 header version, KEK ID, nonce, AAD, DEK cache TTL, key rotation, 평문 호환 기간과 rollback 기준을 명시한다.
- 결론과 근거는 이 PDCA 문서에만 기록한다. 제품 동작은 변경하지 않는다.

## Out of Scope

- Kafka client interceptor, serializer, KMS client 또는 production secret의 구현·배포.
- 사용자 메시지 E2EE, DB-at-rest 암호화, TLS/ACL 대체.
- 암호화가 필요하다는 근거 없는 공통 crypto framework 추가.

## Success Criteria

- 모든 Gateway Kafka producer payload와 consumer/DLQ 경로가 표로 남는다.
- 선택된 보안 경계, 공격자 모델, KMS 권한 모델 및 key-compromise 영향이 명시된다.
- 암호화 선택 시 후속 구현이 충족할 불변조건과 negative test가 구체화된다.
- 암호화 미선택 시 허용 payload 계약과 이를 지킬 테스트 위치가 명시된다.

## Failure Criteria

- 'E2EE'라는 명칭만으로 KMS/키 배포를 도입한다.
- Kafka value만 보고 key, header, DLQ, log, backup 복제 경로를 누락한다.
- 개인키 또는 원시 DEK를 애플리케이션 설정, 코드, 테스트 fixture에 둔다.

## Dependencies And Follow-up

- T171-D의 표준 event/schema owner 및 ordering/idempotency 결정과 정합성을 확인한다.
- T191 승인 뒤에만 T192 `Kafka Gateway Envelope Encryption`을 생성한다.
