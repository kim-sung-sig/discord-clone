# T44 Message Search & Moderation Reports Design

Date: 2026-05-18
Slice: T44 Message Search & Moderation Reports

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Architecture

T44 stays inside the existing in-memory domain modules:

- `backend/modules/message`: search boundary and query behavior.
- `backend/modules/moderation`: report queue, report status transition, and audit linkage.

No boot controller or frontend UI is added in this slice. That keeps the first implementation focused on the behavioral contracts that later REST/UI work can consume.

## Message Search Design

Existing `search(guildId, channelId, query, limit)` remains available for channel-local search. T44 adds guild search with an explicit authorized channel set:

```java
search(UUID guildId, Set<UUID> allowedChannelIds, String query, int limit)
```

The service filters by guild, non-deleted state, content match, and membership in `allowedChannelIds`. Empty query or empty allowed-channel set returns an empty result. Results stay newest-first and limit bounded by existing page-size rules.

## Moderation Report Design

The moderation module gains:

- `MessageReportStatus`: `OPEN`, `RESOLVED`, `DISMISSED`
- `MessageReport`: immutable report record with ids, reason, status, timestamps, and moderator resolution metadata
- `ReportMessageCommand`: report creation input

`InMemoryModerationService` stores reports by guild. Reporting creates an `OPEN` report and appends `MESSAGE_REPORTED` audit. Resolving a report changes status to `RESOLVED` or `DISMISSED` and appends `MESSAGE_REPORT_RESOLVED`.

## Incident Timeline

For the first slice, the incident timeline is represented by two read models:

- `messageReports(guildId, messageId)` for report history
- `auditLogs(guildId, null, null, messageId)` for message-targeted audit entries

The report keeps only ids and reason. It does not duplicate message content, reducing privacy leakage if a message is deleted later.

## Testing

TDD tests cover:

- Guild search does not leak messages from channels outside the allowed set.
- Guild search ignores deleted messages.
- Reporting creates pending queue item plus audit entry.
- Resolving report removes it from pending queue and writes moderator audit.
