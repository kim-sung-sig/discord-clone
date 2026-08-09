# T171-C0 첫 웨이브 통과 서명

## 판정

- 상태: **PASS — 다음 설계 웨이브 착수 가능, 제품 구현은 별도 승인 필요**
- 기준: 각 독립 검수 90점 이상, P0/P1 0건
- 제품 테스트: 미실행. 이번 서명은 문서·코드 정적 대조와 계약 검수에 한정한다.

## 독립 검수 증거

| 검수 | 결과 | 증거 |
|---|---:|---|
| 최초 C0 스펙 | 92/100, P0/P1 0 | `T171-C0-wave-review-spec.md` |
| 최초 C1/C3 품질 | C1 94, C3 96, 통합 95, P0/P1 0 | `T171-C1-C3-wave-review-quality.md` |
| 보정 C0 스펙 | 95/100, P0/P1/P2 0 | `T171-C0-wave-review-spec-r2.md` |
| 보정 C0 품질·보안 | 94/100, P0/P1 0, P2 1 | `T171-C0-wave-review-quality-r2.md` |

## 보정된 게이트

- JWT: `sid`, `authzVersion`, 15분 TTL, refresh rotation/revoke/reuse, legacy 1시간 토큰 종료 기준, `kid` rotation 검증.
- 이벤트: 원본 `eventId` 보존, Kafka `acks=all`·idempotence·bounded timeout ACK 후 `published_at`, inbox unique와 projection 동일 transaction, retry/DLQ.
- 위 계약은 실제 테스트·설정·DDL·로그 증거가 없으면 구현 완료 또는 legacy 종료를 선언하지 않는다.

## 다음 웨이브

다음 작업 후보는 C2 RBAC projection 계약과 C4 DB 샤딩·replica·partition 실측 설계다. 이 acceptance는 두 작업의 구현 완료·커밋·푸시·머지를 승인하지 않는다.
