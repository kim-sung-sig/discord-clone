# Discord Clone Platform Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
범위: Spring Boot backend + Nuxt frontend 기반 Discord 클론 엔터프라이즈 마스터 플랜

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | Discord 수준의 서버/채널/채팅/음성/권한/커뮤니티 기능은 단일 CRUD 앱으로 만들 수 없다. 실시간 이벤트, 권한 계산, 메시지 저장, 미디어, 커뮤니티 운영을 분리 설계해야 한다. |
| Solution | 초기에는 모듈러 모놀리스 + 명확한 bounded context로 시작하고, Gateway/Media/Search/Storage는 독립 인프라로 둔다. 이후 트래픽 기준에 따라 마이크로서비스로 분리한다. |
| Function UX Effect | 사용자는 Discord와 유사한 좌측 서버 목록, 채널 트리, 중앙 채팅, 우측 멤버 목록, 하단 사용자/보이스 상태 UI를 경험한다. |
| Core Value | 구현 속도와 엔터프라이즈 확장성을 동시에 확보한다. 각 task는 TDD, 아키텍처 테스트, 화면 테스트, QA 피드백 루프를 통과해야 완료된다. |

## 1. 목표

Spring Boot와 Nuxt를 기반으로 Discord 핵심 경험을 제공하는 클론 플랫폼을 구축한다.

핵심 목표:

- Discord 기능 분석 보고서를 기준으로 제품 범위를 정의한다.
- 엔터프라이즈급 아키텍처와 테스트 하네스를 먼저 설비한다.
- 모든 기능은 PDCA 문서와 task 성공/실패 기준을 가진다.
- 구현은 기능별로 Plan -> Design -> Do -> Check -> Act를 반복한다.
- 구현 전 설계 승인 게이트를 둔다.

## 2. 비목표

- Discord의 내부 구현을 복제하지 않는다.
- Discord API와 호환되는 봇 플랫폼을 1차 목표로 하지 않는다.
- 대규모 글로벌 운영 수준의 멀티 리전 배포는 1차 구현에서 제외한다.
- Nitro/Shop/Quests 결제 기능은 entitlement 모델만 먼저 만들고 실제 결제는 후순위로 둔다.

## 3. 제품 범위

### MVP

- 계정/인증
- 서버 생성/가입/탈퇴
- 채널/카테고리
- 역할/권한
- 초대 링크
- 텍스트 메시지
- WebSocket Gateway
- Nuxt shell UI
- 테스트 하네스

### V1

- 친구/DM/그룹 DM
- presence/typing/read state
- 첨부 파일
- 반응/이모지
- 스레드
- 감사 로그
- 권한 관리 UI

### V2

- 포럼
- 온보딩
- AutoMod
- 이벤트/스테이지
- 보이스 채널 signaling

### V3

- WebRTC SFU 연동
- 영상/화면 공유
- soundboard
- 스티커
- premium entitlement/shop skeleton

## 4. 성공 기준

- backend unit test coverage는 핵심 domain/application layer 기준 80% 이상
- permission engine branch coverage 90% 이상
- API contract test가 OpenAPI spec과 일치
- WebSocket Gateway reconnect/resume 시나리오 테스트 통과
- frontend component test와 Playwright smoke/e2e 통과
- architecture test로 layer violation 0건
- task별 QA 문서에 failure feedback이 없거나 모두 해소됨

## 5. 실패 기준

- 권한 계산이 API와 UI에서 다르게 동작한다.
- WebSocket 이벤트가 유실되거나 중복 처리되어 메시지/상태가 깨진다.
- 메시지 pagination이 중복/누락을 만든다.
- UI가 Discord shell의 핵심 정보구조를 반영하지 못한다.
- 컴파일 성공만으로 task 완료 처리한다.
- QA 실패 내용을 문서화하지 않고 다음 task로 넘어간다.

## 6. PDCA 운영 방식

각 기능은 아래 문서 세트를 가진다.

- `docs/01-plan/features/{feature}.plan.md`
- `docs/02-design/features/{feature}.design.md`
- `docs/03-analysis/{feature}.analysis.md`
- `docs/04-report/{feature}.report.md`
- `docs/05-feedback/{feature}.feedback.md`

PDCA 루프:

1. Plan: 문제, 범위, 성공/실패 기준 정의
2. Design: 도메인 모델, API, DB, 이벤트, UI, 테스트 설계
3. Do: TDD 기반 구현
4. Check: 단위/API/아키텍처/e2e/화면 QA
5. Act: 실패 피드백 문서화 후 재구현

