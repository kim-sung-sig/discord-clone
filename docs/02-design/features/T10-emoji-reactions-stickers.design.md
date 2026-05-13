# T10 Emoji/Reactions/Stickers Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T10 Emoji/Reactions/Stickers

## Backend Architecture

Create `backend/modules/expression` for emoji/sticker/reaction state. Extend existing permission enum with `MANAGE_EXPRESSIONS` and guild service with `canManageExpressions` so custom expression creation follows existing role/owner/admin semantics.

Planned files:

- `backend/modules/expression/build.gradle.kts`
- `backend/modules/expression/src/main/java/com/example/discord/expression/CustomEmoji.java`
- `backend/modules/expression/src/main/java/com/example/discord/expression/Sticker.java`
- `backend/modules/expression/src/main/java/com/example/discord/expression/Reaction.java`
- `backend/modules/expression/src/main/java/com/example/discord/expression/ReactionSummary.java`
- `backend/modules/expression/src/main/java/com/example/discord/expression/ExpressionNotFoundException.java`
- `backend/modules/expression/src/main/java/com/example/discord/expression/InMemoryExpressionService.java`
- `backend/modules/expression/src/test/java/com/example/discord/expression/InMemoryExpressionServiceTest.java`
- Modify `backend/modules/permission/src/main/java/com/example/discord/permission/Permission.java`
- Modify `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`

Boot adapter files:

- `backend/boot/src/main/java/com/example/discord/expression/ExpressionConfiguration.java`
- `backend/boot/src/main/java/com/example/discord/expression/ExpressionController.java`
- `backend/boot/src/test/java/com/example/discord/expression/ExpressionControllerTest.java`
- `settings.gradle.kts`, `backend/boot/build.gradle.kts`

API shape:

- `POST /api/guilds/{guildId}/emojis` body `{ "name": "shipit", "imageObjectKey": "..." }`.
- `GET /api/guilds/{guildId}/emojis`.
- `DELETE /api/guilds/{guildId}/emojis/{emojiId}`.
- `POST /api/guilds/{guildId}/stickers` body `{ "name": "approved", "description": "..." }`.
- `GET /api/guilds/{guildId}/stickers`.
- `PUT /api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}` adds idempotent reaction.
- `DELETE /api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}` removes own reaction.
- `GET /api/channels/{channelId}/messages/{messageId}/reactions` returns summaries.

## Frontend Architecture

Extend the shell message card with reaction chips and expression catalog state.

Planned files:

- `apps/web/components/shell/ReactionBar.vue`
- `apps/web/components/shell/ExpressionPanel.vue`
- `apps/web/stores/shell.ts`
- `apps/web/components/shell/ChatViewport.vue`
- `apps/web/assets/css/main.css`
- `apps/web/tests/components/app-shell.test.ts`
- `apps/web/tests/e2e/app-shell.spec.ts`

## Test Plan

- Backend unit: duplicate reaction add is idempotent, remove is safe, emoji/sticker CRUD validates name.
- Backend MockMvc: custom emoji creation requires `MANAGE_EXPRESSIONS`; owner/admin allowed; list reactions returns count.
- Frontend component: clicking reaction toggles current user membership and count.
- Playwright: add/remove reaction on first visible message and verify count does not double increment.

## Risk Controls

- Reaction mutation methods are synchronized.
- Reaction identity uses `(messageId, emojiKey, userId)` set membership, not count increments.
- Permission extension uses a new bit and existing permission grant rules.
