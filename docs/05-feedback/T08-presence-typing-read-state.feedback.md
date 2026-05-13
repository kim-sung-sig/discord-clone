# T08 Presence/Typing/Read State Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend TDD | Presence must expire to offline deterministically. | Added injected-clock TTL store and offline fallback. |
| Backend TDD | Typing state must not persist forever. | Added expiry timestamps and active typing filtering. |
| Backend TDD | Unread count must be deterministic and exclude authored messages. | Added read marker and sequence-based unread calculation. |
| Frontend QA | Status/unread state must be visible in shell, not only store state. | Added `PresenceBadge`, `TypingIndicator`, and `UnreadBadge` components with component/E2E coverage. |

## Known Non-Blocking Risks

- Actual Redis client integration is deferred; current code uses a Redis-compatible in-memory TTL adapter.
- Presence events are not yet published through Gateway fanout.
- Read markers are not persisted to Postgres yet.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
