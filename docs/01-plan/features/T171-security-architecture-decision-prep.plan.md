# T171 보안 및 아키텍처 결정 준비 계획

Date: 2026-05-31
Status: T171-A/T171-B 구현 완료, T171-C/T171-D/T171-E 결정 대기
Language: Korean
Implementation gate: 남은 C-E 항목은 사용자 결정 전 구현 금지

## 목적

T170 완료 과정에서 다음 보안/아키텍처 결정 항목이 새로 발생했다. 이 문서는 구현 계획이 아니라, 사용자의 명시적 결정을 받기 위한 준비 Task 목록이다.

## 결정 원칙

- 아래 Task는 구현 전에 반드시 사용자에게 선택지를 제시하고 승인을 받아야 한다.
- 승인을 받기 전에는 코드 변경, 인프라 변경, 인증 정책 변경을 하지 않는다.
- 분석 산출물은 한글로 작성하고, 사용자 보고용 문서는 인터랙티브 HTML 형식을 우선 사용한다.

## 우선순위 요약

| Rank | Task | 보안 | 사용성 | 유지보수성 | 기능성 | 합계 | 구현 승인 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| S | T171-A X-Forwarded-For 위험성 검토 | 10 | 6 | 8 | 8 | 32 | 구현 완료 |
| S | T171-B MFA 적용 여부 결정 | 10 | 7 | 7 | 8 | 32 | 구현 완료 |
| A | T171-C MSA 전환 여부 준비 | 6 | 5 | 10 | 7 | 28 | 사용자 결정 필수 |
| A | T171-D 이벤트 드리븐 아키텍처 도입 여부 준비 | 7 | 5 | 9 | 8 | 29 | 사용자 결정 필수 |
| A | T171-E SAGA 도입 여부 준비 | 7 | 5 | 9 | 8 | 29 | 사용자 결정 필수 |

## T171-A X-Forwarded-For 위험성 검토

### 검토 배경

`X-Forwarded-For`는 클라이언트가 임의로 위조할 수 있는 헤더다. 현재 보안 대시보드와 CSP rate-limit subject 계산은 trusted proxy 설정과 밀접하게 연결되어 있으므로, 운영 환경에서 어떤 proxy chain을 신뢰할지 명확히 해야 한다.

### 결정 질문

1. 운영 환경에서 신뢰할 proxy CIDR 목록을 명시할 것인가?
2. trusted proxy 밖에서 들어온 `X-Forwarded-For`는 완전히 무시할 것인가?
3. 감사/보안 UI에서 raw IP 또는 forwarded header를 계속 비노출로 유지할 것인가?
4. proxy misconfiguration을 배포 차단 조건으로 만들 것인가?

### 산출물 후보

- 위험성 분석 HTML 보고서
- trusted proxy 정책 ADR
- rate-limit subject 계산 회귀 테스트
- 운영 환경 설정 체크리스트

### 구현 결과

- `DISCORD_TRUSTED_PROXY_CIDRS` / `discord.trusted-proxy.cidrs` 기반 명시적 trusted proxy 정책을 구현했다.
- production은 trusted proxy CIDR 설정이 없거나 잘못되면 시작 검증에서 실패한다.
- backend API rate-limit subject 계산 테스트와 운영 보고서를 추가했다.

## T171-B MFA 적용 여부 결정

### 검토 배경

T170에서 operator token audit가 추가되면서 `/security` 접근과 operator token bootstrap의 보안 중요도가 높아졌다. MFA를 적용할지, 적용한다면 어떤 경로에 요구할지 결정이 필요하다.

### 결정 질문

1. MFA 적용 대상은 `/security` 전체인가, operator token 발급/폐기 같은 고위험 동작인가?
2. MFA 방식은 TOTP, WebAuthn, 외부 IdP 중 무엇을 우선할 것인가?
3. 백업 코드, 계정 복구, 분실 대응 정책을 포함할 것인가?
4. 개발/로컬 환경에는 MFA를 어떻게 우회 또는 시뮬레이션할 것인가?

### 산출물 후보

- MFA 적용 범위 결정서
- 인증 UX 흐름도
- 보안 회귀 테스트 목록
- 단계적 도입 계획

### 구현 결과

- phase 1 MFA 적용 범위는 operator token exchange/revoke 같은 고위험 동작으로 확정했다.
- backend/JWT principal의 `mfaVerified`, `authTime`, `auth_time`, `amr`, `acr` claim을 MFA evidence로 정규화한다.
- operator token exchange/revoke route는 fresh MFA evidence를 요구하고, read-only `/security` 조회는 기존 dashboard guard를 유지한다.
- production에서는 static MFA bypass를 허용하지 않고, local/QA fixture는 명시적 non-production 설정에서만 동작한다.

## T171-C MSA 전환 여부 준비

### 검토 배경

현재 시스템은 모듈 경계가 늘어나고 있으며, 일부 도메인은 독립 서비스 후보가 될 수 있다. 단, MSA는 운영 복잡도와 장애면을 크게 늘리므로 전환 여부를 별도 결정해야 한다.

### 결정 질문

1. 실제 MSA 전환을 목표로 할 것인가, 모듈러 모놀리스 강화를 우선할 것인가?
2. 우선 분리 후보는 auth, gateway, notification, audit/security telemetry 중 무엇인가?
3. 서비스 간 인증, tracing, 배포, 장애 대응 비용을 감당할 준비가 있는가?
4. 전환 시점은 프로토타입 이후인가, 운영 요구 발생 이후인가?

### 산출물 후보

- 서비스 경계 후보 맵
- 분리 비용/효과 비교표
- MSA 도입 여부 ADR

## T171-D 이벤트 드리븐 아키텍처 도입 여부 준비

### 검토 배경

Gateway, audit, notification, moderation, security telemetry는 이벤트 흐름으로 확장될 가능성이 있다. 기존 Kafka/Redpanda 관련 작업과 연결하되, 무분별한 이벤트화는 피해야 한다.

### 결정 질문

1. 어떤 도메인 이벤트를 먼저 표준화할 것인가?
2. 이벤트 브로커는 기존 Redpanda/Kafka 계열을 유지할 것인가?
3. 이벤트 스키마 소유권과 버전 정책은 어떻게 둘 것인가?
4. at-least-once, ordering, idempotency 기준은 무엇인가?

### 산출물 후보

- 이벤트 후보 목록
- 이벤트 스키마 초안
- idempotency/retry 정책
- 운영 관측성 체크리스트

## T171-E SAGA 도입 여부 준비

### 검토 배경

결제/프리미엄, 초대, 업로드, moderation workflow처럼 여러 리소스 상태가 함께 변하는 기능은 장기적으로 SAGA 후보가 될 수 있다. 아직 도입은 결정되지 않았다.

### 결정 질문

1. SAGA가 필요한 실제 사용자 흐름이 있는가?
2. orchestration과 choreography 중 어떤 방식을 우선할 것인가?
3. 보상 트랜잭션 실패 시 운영자가 개입할 도구가 필요한가?
4. SAGA 상태 저장소와 audit trail을 어디에 둘 것인가?

### 산출물 후보

- SAGA 후보 use case 목록
- 보상 트랜잭션 정책 초안
- 장애/재시도 상태 모델

## 다음 액션

1. 사용자에게 S/A Rank 항목부터 결정 질문을 제시한다.
2. 사용자가 승인한 항목만 별도 goal로 설정한다.
3. 승인된 항목은 설계 문서 또는 ADR을 먼저 작성한다.
4. 구현이 필요한 경우 TDD로 RED 테스트를 먼저 만든다.
