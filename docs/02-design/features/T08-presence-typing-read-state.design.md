# T08 Presence/Typing/Read State Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T08 Presence/Typing/Read State

## Backend Architecture

Create `backend/modules/presence` to keep realtime user state independent from gateway transport and message storage.

Planned files:

- `backend/modules/presence/build.gradle.kts`
- `backend/modules/presence/src/main/java/com/example/discord/presence/PresenceStatus.java`
- `backend/modules/presence/src/main/java/com/example/discord/presence/UserPresence.java`
- `backend/modules/presence/src/main/java/com/example/discord/presence/TypingIndicator.java`
- `backend/modules/presence/src/main/java/com/example/discord/presence/ReadMarker.java`
- `backend/modules/presence/src/main/java/com/example/discord/presence/PresenceTtlStore.java`
- `backend/modules/presence/src/main/java/com/example/discord/presence/InMemoryPresenceTtlStore.java`
- `backend/modules/presence/src/main/java/com/example/discord/presence/InMemoryPresenceService.java`
- `backend/modules/presence/src/test/java/com/example/discord/presence/InMemoryPresenceServiceTest.java`

Boot adapter files:

- `backend/boot/src/main/java/com/example/discord/presence/PresenceConfiguration.java`
- `backend/boot/src/main/java/com/example/discord/presence/PresenceController.java`
- `backend/boot/src/test/java/com/example/discord/presence/PresenceControllerTest.java`
- `settings.gradle.kts`, `backend/boot/build.gradle.kts`

API shape:

- `PUT /api/presence/me` body `{ "status": "ONLINE", "ttlSeconds": 60 }`.
- `GET /api/presence/users/{userId}` returns current status or `OFFLINE` if TTL expired.
- `PUT /api/presence/channels/{channelId}/typing` body `{ "ttlSeconds": 5 }`.
- `GET /api/presence/channels/{channelId}/typing` returns active typing user ids.
- `PUT /api/presence/channels/{channelId}/read` body `{ "lastReadSequence": 42 }`.
- `POST /api/presence/channels/{channelId}/unread-count` body `{ "lastMessageSequence": 50, "authoredSequences": [45] }` returns deterministic unread count.

## Frontend Architecture

Extend existing Nuxt shell data rather than introducing server calls yet.

Planned files:

- `apps/web/components/shell/PresenceBadge.vue`
- `apps/web/components/shell/TypingIndicator.vue`
- `apps/web/components/shell/UnreadBadge.vue`
- `apps/web/stores/shell.ts`
- `apps/web/components/shell/MemberSidebar.vue`
- `apps/web/components/shell/ChannelSidebar.vue`
- `apps/web/components/social/DmSidebar.vue`
- `apps/web/tests/components/app-shell.test.ts`
- `apps/web/tests/e2e/app-shell.spec.ts`

## Test Plan

- Backend unit: presence TTL expiry, typing expiry, unread count excludes own messages and advances on read marker.
- Backend MockMvc: authenticated update/read endpoints.
- Frontend component: badges render status/unread/typing and mutate on read action.
- Playwright: channel unread clears after selecting/marking read and typing indicator is visible.

## Risk Controls

- Keep TTL calculation deterministic with injected `Clock`.
- Keep Redis replacement behind `PresenceTtlStore`.
- Do not overload gateway module; presence service is transport-agnostic.
