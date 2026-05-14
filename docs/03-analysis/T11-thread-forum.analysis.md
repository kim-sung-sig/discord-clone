# T11 Thread/Forum Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T11 Thread/Forum

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| public/private thread | Met | `ThreadType.PUBLIC`/`PRIVATE`, `InMemoryThreadServiceTest`, and `ThreadList` render public/private labels |
| parent permission inheritance | Met | `ThreadControllerTest.inheritsParentViewAndSendPermissionsWhenCreatingThread` denies hidden parent channels and allows visible send-enabled parents |
| archive/reopen lifecycle | Met | domain test covers archive/reopen timestamps and REST test rejects archived writes |
| auto archive | Met | `archiveExpired()` archives inactive threads after configured minutes |
| forum post/tag/guidelines/layout | Met | backend `forum-tags`/`forum-posts` APIs and Nuxt `ForumPanel` render guidelines, tags, and list layout |
| forum tag requirement | Met | domain and REST tests reject missing or unknown tags |
| forum UI e2e | Met | Playwright `manages forum tags and archived thread writes` verifies guidelines, tags, archive guard, reopen, and post creation |

## Gap Log

- Resolved: initial backend worker implementation had domain tag support but no REST path to create an allowed tag. Added `POST /api/channels/{forumChannelId}/forum-tags`.
- Resolved: forum post/tag APIs could have accepted non-forum parent channels. Added `GUILD_FORUM` channel type validation through `InMemoryGuildService.channel`.
- Resolved: Playwright clicked SSR-rendered forum buttons before Nuxt hydration. Added mounted hydration guards so E2E waits for enabled interactive controls.

## Residual Risks

- Thread/forum persistence is in-memory; durable tables and unique constraints are deferred.
- Thread messages currently return write receipts rather than persisting full message records.
- Forum layout is list-only skeleton; gallery/inactive hide behavior is deferred.
- Gateway fanout for thread lifecycle events is deferred.
