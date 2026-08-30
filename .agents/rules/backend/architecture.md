---
id: backend-architecture-evidence
description: 백엔드 아키텍처 설계에서 프레임워크 실제 동작과 코드 근거를 우선한다.
appliesTo: [backend, docs]
triggers: [architecture, redis, session, jwt, rbac, security, msa]
---
# 백엔드 아키텍처 근거 규칙

- Spring Session, Spring Security, Redis, JWT처럼 프레임워크가 상태·키·직렬화·수명을 소유하는 영역은 임의 key 구조나 동작을 설계하지 않는다.
- 설계 전 현재 의존성, `SessionRepository`/Security 설정, cookie·JWT claim, serialization, TTL, 서비스 간 namespace를 코드와 설정에서 확인한다.
- 세션 저장소, 권한 원본, 권한 projection/cache, 토큰 검증을 서로 다른 책임으로 구분한다. 같은 Redis를 사용해도 같은 데이터 모델로 취급하지 않는다.
- 서비스 분리·보안·데이터 정합성 설계는 실제 호출 경로와 failure mode를 확인한 뒤에만 확정한다. 확인 전에는 가정을 사실처럼 말하지 않는다.
