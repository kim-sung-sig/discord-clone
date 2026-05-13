# T07 Friendship/DM/Group DM Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T07 Friendship/DM/Group DM

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 현재 플랫폼에는 사용자 간 관계, 차단, 1:1 DM, 그룹 DM 권한 모델이 없어 Discord 핵심 개인 커뮤니케이션 UX를 검증할 수 없다. |
| Solution | 신규 `social` backend module에 friendship/block/DM/group DM aggregate를 만들고, boot REST adapter와 Nuxt DM list panel을 추가한다. |
| Function UX Effect | 사용자는 친구 요청 상태, 차단 상태, DM/group DM 항목, group call skeleton 상태를 화면과 API 테스트로 확인할 수 있다. |
| Core Value | T08 presence/typing/read state와 이후 voice/group call 기능이 참조할 개인 채널/멤버십 권한 기반을 만든다. |

## Scope

- Friend request lifecycle: send, accept, decline/cancel, list by requester.
- Block/privacy: block prevents new DM message attempts, invite attempts, and mention eligibility in social channel policy.
- DM channel: deterministic 1:1 DM between two unblocked users.
- Group DM: create with owner, add/remove members with owner authorization and membership invariants.
- Group call state skeleton: idle/active state with participants list, no real audio transport.
- Nuxt UI: DM list region showing friends, blocked users, direct DM, group DM, and group call status.

## Out of Scope

- Persisted database schema; current architecture is still in-memory TDD modules.
- Real WebSocket fanout for DMs; Gateway event integration follows T08/T09.
- Full message body persistence for DM; T07 validates permission/channel lifecycle and UI state skeleton.

## Success Criteria

- Backend unit tests prove blocked users cannot open/send through DM policy.
- Backend controller tests prove friend request transitions and group member add/remove authorization.
- Frontend component test proves DM list renders friendship, block, direct DM, group DM, and group call state.
- Playwright e2e proves opening group DM and member add/remove UI state.
- Full gates: `./gradlew.bat test --rerun-tasks`, `npm run test -w apps/web -- --run`, `npm run build -w apps/web`, `npm run e2e -w apps/web`.

## Failure Criteria

- Friend request can skip invalid states such as accept without pending request.
- Blocked user can create DM/send DM/start invite/receive mention eligibility.
- Non-owner can add/remove group DM members.
- Owner can remove themselves while other members remain, causing orphaned group DM.
- DM list UI is static but not connected to store actions/tests.

## Delivery Strategy

1. Backend social domain TDD: records, service, blocked policy, group membership invariants.
2. Boot social API TDD: MockMvc coverage for friendship, block, DM create/send policy, group member authorization.
3. Nuxt DM shell TDD: store state/actions, `DmSidebar` component, component/e2e tests.
4. PDCA Check/Report/Feedback with fresh verification evidence.
