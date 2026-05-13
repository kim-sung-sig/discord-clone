# T08 Presence/Typing/Read State Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T08 Presence/Typing/Read State

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| online/idle/dnd/offline | Met | `PresenceStatus`, TTL store, backend tests, and Nuxt `PresenceBadge` |
| Redis TTL presence test | Met | `InMemoryRedisPresenceTtlStore` and service tests prove TTL expiry to offline with injected clock |
| typing expires automatically | Met | backend typing expiry tests and active-channel frontend typing tests |
| read marker | Met | backend read marker endpoint/model and Pinia `markChannelRead` |
| unread count deterministic | Met | backend unread count tests and frontend unread badge/clear tests |
| UI badge/component test | Met | Vitest covers presence badges, typing indicator, unread badge, and mark-read behavior |

## Gap Log

- Resolved: no status TTL model existed; added replaceable `PresenceTtlStore` and Redis-compatible in-memory implementation.
- Resolved: typing events could become permanent if modeled as plain state; typing records now carry expiry.
- Resolved: unread count had no deterministic rule; count now uses sequence/read marker and excludes current user's messages.
- Resolved: UI lacked status/unread affordances; added reusable badge/indicator components.

## Residual Risks

- Actual Redis client binding is deferred; current adapter verifies Redis-compatible TTL semantics without external dependency.
- Presence/read/typing state is in-memory and not Gateway-broadcast yet.
- Read markers are sequence-based skeletons and should be tied to persisted message sequence once message persistence lands.
