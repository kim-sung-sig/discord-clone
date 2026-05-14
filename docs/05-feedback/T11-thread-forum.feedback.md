# T11 Thread/Forum Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend review | Domain supported forum tags, but REST could not create allowed tags for forum posts. | Added `POST /api/channels/{forumChannelId}/forum-tags` and tagged forum post regression test. |
| Backend review | Forum post/tag endpoints should reject non-forum channels. | Added `InMemoryGuildService.channel` lookup and `GUILD_FORUM` validation in `ThreadController`. |
| Frontend E2E | Thread row clicks could happen before Nuxt hydration, losing the first click. | Added mounted hydration guards to forum action and thread list buttons. |

## Known Non-Blocking Risks

- Thread/forum storage is in-memory and must move to durable persistence later.
- Thread message writes do not yet integrate with the main message timeline.
- Forum gallery layout and inactive hide behavior remain skeleton-level.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
