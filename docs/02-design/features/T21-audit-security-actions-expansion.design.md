# T21 Audit/Security Actions Expansion Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T21 Audit/Security Actions Expansion

## Architecture Decision

Use `InMemoryModerationService` as the current audit/security control boundary. It already owns AutoMod policy decisions and guild audit logs, so T21 expands it with a narrow query API and security alert list without introducing a separate service before persistence is unified.

## Audit Search API

`GET /api/guilds/{guildId}/audit-logs` accepts optional query parameters:

- `action`
- `actorId`
- `targetId`

Filters are AND-composed and sorted newest-first as today.

## Audit Coverage Hooks

- `GuildController.assignRoleToMember` emits `ROLE_ASSIGNED`.
- `InviteController.deleteInvite` emits `INVITE_DELETED`.
- `MessageController.delete` emits `MESSAGE_DELETED`; `pin` emits `MESSAGE_PINNED`; `unpin` emits `MESSAGE_UNPINNED`.
- `StageController.approveSpeaker` emits `STAGE_SPEAKER_APPROVED`; `moveToAudience` emits `STAGE_AUDIENCE_MOVED`.

All events include guild id, actor id, target id, action, reason, and createdAt through existing `AuditLogEntry`.

## Security Alert Skeleton

Add `SecurityAlert` in moderation module:

- id
- guildId
- actorId
- targetId
- type
- severity
- reason
- createdAt

AutoMod blocked message creates `AUTOMOD_BLOCK` alert at the same policy-decision boundary before returning blocked. Since `MessageController` checks AutoMod before `messageService.create`, the alert proves policy detection without false message persistence.

## Test Strategy

- MockMvc test for searchable audit actions: role assignment, invite delete, message delete, stage approval.
- MockMvc test for filtering by action and actor.
- MockMvc test for AutoMod alert and absent blocked message.
- Existing moderation tests remain green.

## Risks

- In-memory audit/alert store is still not durable.
- Some privileged endpoints remain uncovered until a full audit matrix pass.
- Alert severity taxonomy is intentionally minimal.
