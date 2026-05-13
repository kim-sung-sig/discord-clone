# T02 Guild/Channel/Permission Plan

작성일: 2026-05-13  
PDCA Phase: Plan  
Slice: T02-A foundation

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | Discord의 서버/채널 UX는 권한 계산과 채널 가시성에 의존한다. 이 기반이 없으면 메시지, 초대, DM, Gateway 이벤트가 모두 잘못 노출된다. |
| Solution | `guild`, `channel`, `permission` context를 확장해 guild/member/role/channel/overwrite 모델과 권한 계산 API를 먼저 테스트로 고정한다. |
| Function UX Effect | Nuxt shell은 정적 seed에서 guild/channel 구조를 명시적으로 표현하며, 이후 API 연동을 위한 UI 상태 구조를 갖춘다. |
| Core Value | 권한 계산과 채널 가시성 회귀를 초기부터 차단하고, 다음 메시지/Gateway 작업의 기반을 만든다. |

## Scope

- guild 생성
- default owner membership
- default `@everyone` role
- role permission assignment
- text/voice/category channel model
- channel permission overwrite
- effective permission calculation
- channel visibility filtering
- REST API foundation for guild/channel read/write
- Nuxt shell state update for guild/channel terminology

## Out of Scope

- persistence/JPA
- complete Discord permission enum
- role management UI
- drag/drop channel ordering
- invite flow
- message send/read

## Success Criteria

- permission truth table tests cover allow/deny/administrator/channel overwrite behavior
- guild creation creates owner member and default role
- channel visibility API hides channels without `VIEW_CHANNEL`
- REST tests cover guild create, channel create, visible channel list
- frontend component/e2e still pass and display guild/channel structure
- full backend/frontend/infra verification passes

## Failure Criteria

- UI shows a channel that backend permission denies
- channel overwrite deny does not override role allow
- administrator does not bypass channel denies
- owner membership is not created with guild
- tests only compile without behavior assertions

