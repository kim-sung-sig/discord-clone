# T04 Message Core Analysis

작성일: 2026-05-13  
PDCA Phase: Check  
Slice: T04 message core

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| Message create/update/delete | Met | `MessageControllerTest` author/manager mutation tests and `InMemoryMessageServiceTest` lifecycle tests |
| Cursor pagination | Met | service test validates newest-first cursor pagination without duplicates/missing messages |
| Mention extraction | Met | service test validates `<@uuid>` and `@username`; frontend test validates email exclusion and case-insensitive dedupe |
| Pin/unpin | Met | service and MockMvc tests validate pin/unpin and `MANAGE_MESSAGES` authorization |
| Edit history | Met | service test validates edit history and deletion clears prior content/history |
| Basic search | Met | service test validates case-insensitive non-deleted content search |
| Permission denied read/write | Met | MockMvc tests validate `VIEW_CHANNEL`, `SEND_MESSAGES`, and `MANAGE_MESSAGES` boundaries |
| Chat viewport metadata/composer | Met | Vitest and Playwright cover pinned/edited/tombstone/mentions/composer/send |

## Gap Log

- Resolved: `MANAGE_MESSAGES` was missing from the permission enum and moderation initially reused `MANAGE_CHANNELS`.
- Resolved: deleted edited messages retained edit history; delete now clears history to avoid content leakage.
- Resolved: author edit/delete now requires current channel access; edit also requires current send permission.
- Resolved: message moderation now requires both `VIEW_CHANNEL` and `MANAGE_MESSAGES`.
- Resolved: frontend chat viewport now wraps message rows in a stable `message-list` grid region.
- Resolved: frontend mention extraction no longer matches email domains and dedupes case-insensitively.
