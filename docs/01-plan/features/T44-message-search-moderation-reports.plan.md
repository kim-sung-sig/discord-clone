# T44 Message Search & Moderation Reports Plan

Date: 2026-05-18
Slice: T44 Message Search & Moderation Reports

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T44 expands the Discord clone with message discovery and user report moderation. The task breakdown defines three hard requirements:

- Search must only return messages from channels the requester can access.
- Users must be able to report a message and moderators must process reports from a queue.
- Report handling must leave an audit/security trail.

## Implementation Plan

Major topics:

1. Message search authorization boundary
   - Add guild-scoped search across a caller-provided allowed channel set.
   - Keep deleted messages and unauthorized channels out of results.
   - Preserve the existing channel-scoped search API.

2. Moderation report queue
   - Add message report creation with reporter, message, channel, guild, reason, and status.
   - Add moderator resolution flow.
   - Expose pending report queue and message-specific incident timeline.

3. Audit linkage
   - Add audit actions for report creation and resolution.
   - Ensure report lifecycle actions can be queried through existing audit logs.

## Out of Scope

- Frontend search UI.
- PostgreSQL full-text indexes.
- Attachment/content safety scanning.
- Moderator role permission checks inside the domain module; API layer will provide authorized caller context later.

## Acceptance Criteria

- A guild search with allowed channel ids never returns messages from unauthorized channels.
- Deleted messages do not appear in search results.
- Reporting a message creates a pending report and audit entry.
- Resolving a report updates queue state and creates a moderator audit entry.
- T44 analysis/report/feedback documents record the review score and residual tasks.

## Failure Criteria

- Search leaks inaccessible channel content.
- Report resolution removes queue state without audit trace.
- Report objects store unnecessary message content snapshots.
