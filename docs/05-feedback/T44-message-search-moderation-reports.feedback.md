# T44 Message Search & Moderation Reports Feedback

Date: 2026-05-18
Slice: T44 Message Search & Moderation Reports

## Feedback Items

| Id | Priority | Observation | Proposed Task |
| --- | --- | --- | --- |
| T44-FB-001 | High | Guild search is in-memory and substring-based. | T72 PostgreSQL full-text message search adapter. |
| T44-FB-002 | High | Search/report behavior has no REST/OpenAPI contract yet. | T73 message search and report REST API. |
| T44-FB-003 | High | Domain search relies on caller-provided allowed channel ids. | T74 API-layer permission integration for message search. |
| T44-FB-004 | Medium | Moderators have no web moderation queue or incident timeline UI. | T75 moderation queue UI and incident timeline. |
| T44-FB-005 | Medium | Report resolution has terminal status handling but no action-specific policy such as delete/timeout/ban. | Add moderation action workflow after admin console permissions are available. |

## Loop Decision

T44 scored 27/30 and passed the threshold. Continue to the next planned task unless the immediate priority is REST/UI integration for T44.
