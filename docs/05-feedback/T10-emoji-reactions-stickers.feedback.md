# T10 Emoji/Reactions/Stickers Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend review | Reaction endpoints verified channel visibility but not message existence. | Added regression test and `InMemoryMessageService` validation before add/remove/list. |
| Backend TDD | Duplicate reaction add must not increment count twice. | Reaction counts derive from unique reactor ID set. |
| Backend TDD | Custom emoji/sticker creation requires a dedicated permission. | Added `MANAGE_EXPRESSIONS` and guild service permission check. |
| Frontend TDD | Reaction UI must mutate store state, not render static chips. | Added `ReactionBar` toggle actions and Playwright reaction smoke. |

## Known Non-Blocking Risks

- Emoji/sticker assets are metadata skeletons; storage/media processing integration is deferred.
- Reaction persistence and database uniqueness constraints are deferred.
- Gateway reaction fanout is deferred.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
