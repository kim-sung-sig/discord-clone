# T11 Thread/Forum Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T11 Thread/Forum

## Backend Architecture

Create `backend/modules/thread` for thread/forum state. Keep permission decisions in boot adapter by consulting `InMemoryGuildService` parent channel permissions, while the domain module enforces lifecycle and tag invariants.

Planned files:

- `backend/modules/thread/build.gradle.kts`
- `backend/modules/thread/src/main/java/com/example/discord/thread/ThreadType.java`
- `backend/modules/thread/src/main/java/com/example/discord/thread/ThreadChannel.java`
- `backend/modules/thread/src/main/java/com/example/discord/thread/ForumTag.java`
- `backend/modules/thread/src/main/java/com/example/discord/thread/ForumPost.java`
- `backend/modules/thread/src/main/java/com/example/discord/thread/ThreadWriteReceipt.java`
- `backend/modules/thread/src/main/java/com/example/discord/thread/ThreadNotFoundException.java`
- `backend/modules/thread/src/main/java/com/example/discord/thread/InMemoryThreadService.java`
- `backend/modules/thread/src/test/java/com/example/discord/thread/InMemoryThreadServiceTest.java`
- Modify `backend/modules/channel/src/main/java/com/example/discord/channel/ChannelType.java` to add `GUILD_FORUM`.

Boot adapter files:

- `backend/boot/src/main/java/com/example/discord/thread/ThreadConfiguration.java`
- `backend/boot/src/main/java/com/example/discord/thread/ThreadController.java`
- `backend/boot/src/test/java/com/example/discord/thread/ThreadControllerTest.java`
- `settings.gradle.kts`, `backend/boot/build.gradle.kts`

API shape:

- `POST /api/channels/{parentChannelId}/threads` body `{ "name": "...", "type": "PUBLIC", "autoArchiveMinutes": 60 }`.
- `POST /api/channels/{forumChannelId}/forum-posts` body `{ "title": "...", "tagIds": [...] }`.
- `POST /api/threads/{threadId}/messages` body `{ "content": "..." }` returns write receipt if not archived and parent send permission passes.
- `PUT /api/threads/{threadId}/archive` archives thread.
- `PUT /api/threads/{threadId}/reopen` reopens thread if parent send/manage permission passes.
- `POST /api/threads/archive-expired` deterministic maintenance endpoint for tests.

## Frontend Architecture

Add a forum/thread panel to the existing shell. Use store-backed deterministic data and actions for archive/reopen/write skeleton.

Planned files:

- `apps/web/components/shell/ForumPanel.vue`
- `apps/web/components/shell/ThreadList.vue`
- `apps/web/stores/shell.ts`
- `apps/web/pages/app.vue`
- `apps/web/assets/css/main.css`
- `apps/web/tests/components/app-shell.test.ts`
- `apps/web/tests/e2e/app-shell.spec.ts`
- `apps/web/tests/components/story-index.test.ts` only if adding stories.

## Test Plan

- Backend unit: archived thread blocks writes, reopen allows writes, forum post requires allowed tag, auto archive by clock.
- Backend MockMvc: parent channel view/send permission inherited; archived thread write forbidden; forum tag requirement enforced.
- Frontend component: forum tags/guidelines, archived badge, reopen action, write receipt state.
- Playwright: open forum panel, attempt archived write blocked, reopen thread, send thread message skeleton.

## Risk Controls

- Thread domain stores parent channel ID but does not duplicate guild permission logic.
- Boot adapter validates parent channel belongs to guild through `guildIdForChannel` and permission service.
- Archive state is checked before every write.
