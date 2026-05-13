# T04 Message Core Feedback

작성일: 2026-05-13

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| User | Existing Postgres may already own port 5432; use `dev_user` / `dev_password`. | Updated compose defaults and init SQL before T04 implementation. |
| Backend worker concern | `MANAGE_MESSAGES` was absent, so implementation temporarily reused `MANAGE_CHANNELS`. | Added `MANAGE_MESSAGES` permission and regression tests proving `MANAGE_CHANNELS` cannot moderate messages. |
| Backend review | Deleted edited messages exposed prior content via edit history. | Delete now clears content and edit history; service regression test added. |
| Backend review | Author edit/delete and manager pin/delete missed current channel view/send boundaries. | Author edit requires `SEND_MESSAGES`; author delete requires `VIEW_CHANNEL`; moderation requires both `VIEW_CHANNEL` and `MANAGE_MESSAGES`. |
| Backend review | Mention extraction only supported `<@uuid>`, missing `@username`. | Mentions are now string tokens covering UUID mentions and username mentions. |
| Frontend review | Chat grid placed each message as a grid row, which could displace composer. | Wrapped messages in a `message-list` region. |
| Frontend review | Mention extraction matched email domains and was case-sensitive. | Added stricter mention regex and lower-case dedupe. |
| Frontend review | Empty guard and channel-specific message isolation were under-tested. | Added component/e2e assertions for empty submit, active-channel send scoping, and channel message exclusion. |

## Known Non-Blocking Risks

- Message state is in-memory for this slice.
- Gateway event fanout is deferred to T05.
- Frontend API integration is deferred; T04 UI uses verified shell seed/action state.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
