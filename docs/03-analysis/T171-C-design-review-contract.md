# T171-C 설계 검수 계약

## 대상과 revision lock

- 대상: `docs/02-design/features/T171-C-msa-scaleout-ack-architecture.adr.md`, `docs/01-plan/features/T171-C-msa-scaleout-implementation.plan.md`
- 시작 revision: ADR `e84090f9eee431309ee5cf9aa1d16f6cb9691df8`, Plan `ec68aabdf0a21fcb8e8097e1edebbf0e79cd7fd2`
- 리뷰 중 대상 문서는 변경하지 않는다. 리뷰 완료 후 P0/P1을 한 번에 수정하고 새 hash를 기록한다.

## 리뷰어와 산출물

| 역할 | 검수 범위 | 통과 기준 | 산출물 |
| --- | --- | --- | --- |
| Spec Architect | 서비스 책임, 데이터 소유권, event/API 계약, migration, 단계별 전환 | 90/100 이상, P0/P1 없음 | `docs/03-analysis/T171-C/spec-review-r<N>.md` |
| Security Architect | JWT·mTLS binding, ACK bypass, operator, secret/log, trust boundary | 90/100 이상, P0/P1 없음 | `docs/03-analysis/T171-C/security-review-r<N>.md` |
| SRE Architect | K8s renderer, Service/Ingress/NetworkPolicy, cert rotation, HPA/PDB, drill | 90/100 이상, P0/P1 없음 | `docs/03-analysis/T171-C/sre-review-r<N>.md` |
| Principal Backend | Java/Spring module boundary, Kafka/Redis/PostgreSQL correctness, testability | 90/100 이상, P0/P1 없음 | `docs/03-analysis/T171-C/principal-review-r<N>.md` |

## 리뷰 출력 형식

각 산출물은 대상 hash, 검토 파일, 점수표, P0/P1/P2(경로·문단·재현/검증·필수 수정), Blueprint Alignment, Required Loop Action, Acceptance를 포함한다. 동일 revision의 네 산출물이 모두 통과하기 전에는 계획을 승인·커밋·푸시·구현하지 않는다.
