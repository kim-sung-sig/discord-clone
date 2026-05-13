# T07 Friendship/DM/Group DM Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T07 Friendship/DM/Group DM

## Backend Architecture

Create `backend/modules/social` as a focused in-memory domain module. It owns user-to-user relationship state and private channel membership state so guild/channel permission code stays isolated from DM-specific privacy rules.

Planned files:

- `backend/modules/social/build.gradle.kts`: java-library test dependencies.
- `backend/modules/social/src/main/java/com/example/discord/social/FriendshipStatus.java`: `PENDING`, `ACCEPTED`, `DECLINED`, `BLOCKED`.
- `backend/modules/social/src/main/java/com/example/discord/social/FriendRequest.java`: requester/addressee/status/timestamps.
- `backend/modules/social/src/main/java/com/example/discord/social/DirectMessageChannel.java`: deterministic 1:1 channel with participant pair.
- `backend/modules/social/src/main/java/com/example/discord/social/GroupDmChannel.java`: owner, members, call state.
- `backend/modules/social/src/main/java/com/example/discord/social/GroupCallState.java`: active boolean and participants.
- `backend/modules/social/src/main/java/com/example/discord/social/InMemorySocialService.java`: synchronized aggregate service and policy checks.
- `backend/modules/social/src/test/java/com/example/discord/social/InMemorySocialServiceTest.java`: unit TDD.

Boot adapter files:

- `backend/boot/src/main/java/com/example/discord/social/SocialConfiguration.java`: bean registration.
- `backend/boot/src/main/java/com/example/discord/social/SocialController.java`: `/api/social/*` REST endpoints.
- `backend/boot/src/test/java/com/example/discord/social/SocialControllerTest.java`: MockMvc flow tests.
- `settings.gradle.kts`, `backend/boot/build.gradle.kts`: module include/dependency.

API shape:

- `POST /api/social/friends/requests` body `{ "targetUserId": "..." }` returns pending request.
- `PUT /api/social/friends/requests/{requestId}/accept` accepts only addressee.
- `PUT /api/social/friends/requests/{requestId}/decline` declines addressee or cancels requester.
- `PUT /api/social/blocks/{targetUserId}` blocks target and invalidates pending friendship/DM send policy.
- `POST /api/social/dms` body `{ "targetUserId": "..." }` creates/returns 1:1 DM if neither side blocks the other.
- `POST /api/social/dms/{dmId}/messages` body `{ "content": "..." }` validates send policy only and returns accepted skeleton.
- `POST /api/social/group-dms` body `{ "name": "...", "memberIds": [...] }` creates owner-led group DM.
- `PUT /api/social/group-dms/{groupId}/members/{memberId}` owner-only add.
- `DELETE /api/social/group-dms/{groupId}/members/{memberId}` owner-only remove, cannot orphan owner.
- `PUT /api/social/group-dms/{groupId}/call` body `{ "active": true }` owner/member starts/stops skeleton call state.

## Frontend Architecture

Add a DM region to the existing shell without replacing guild/channel UI.

Planned files:

- `apps/web/components/social/DmSidebar.vue`: friend/block/DM/group DM/call summary and buttons.
- `apps/web/components/social/DmSidebar.stories.ts`: framework-light story.
- `apps/web/stores/shell.ts`: add `social` state and actions for selecting direct/group DM and group member mutation.
- `apps/web/pages/app.vue`: render `DmSidebar` inside workspace.
- `apps/web/assets/css/main.css`: grid and responsive styling for DM panel.
- `apps/web/tests/components/app-shell.test.ts`: component assertions for DM list and actions.
- `apps/web/tests/e2e/app-shell.spec.ts`: group DM open/member add/remove smoke.
- `apps/web/tests/components/story-index.test.ts`: include social story.

## Test Plan

- Backend unit RED/GREEN: blocked user cannot DM, invalid friend transitions fail, group member mutation owner-only.
- Backend MockMvc RED/GREEN: friend accept, block prevents DM send, group member add/remove authorization.
- Frontend RED/GREEN: DM list visible and store actions mutate selected group DM/call state.
- E2E RED/GREEN: user opens group DM and toggles call/member UI state.

## Risk Controls

- Use deterministic UUIDs in tests where possible.
- Keep social module independent; no circular dependency with message/guild modules.
- Expose send policy endpoint in T07 but defer full DM message persistence to avoid duplicating message module prematurely.
