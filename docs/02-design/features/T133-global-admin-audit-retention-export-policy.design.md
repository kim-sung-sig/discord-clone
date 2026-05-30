# T133 Global Admin Audit Retention And Export Policy Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T133 Global Admin Audit Retention And Export Policy

## API Contract

`GET /api/admin/global-roles/audit-log` remains the review/export endpoint.

The response now contains:

| Field | Meaning |
| --- | --- |
| `entries` | Latest retained audit entries, already bounded by `limit` 1..100. |
| `retention.maxAgeDays` | Active retention window, currently 365 days. |
| `retention.retainsSince` | Server-computed cutoff timestamp. |
| `export.formats` | Supported export formats, currently `json`. |
| `export.maxEntriesPerRequest` | Maximum export entries per request, currently 100. |
| `export.requiresRole` | Required backend role, currently `SECURITY_ADMIN`. |

## Retention Behavior

The service computes `clock.instant() - 365 days` and omits older entries from the response. Store-level physical pruning is intentionally deferred so backup/PITR evidence remains possible.

## Security Review

- The endpoint remains guarded by backend-owned `SECURITY_ADMIN`.
- Export is the existing JSON response; no separate unauthenticated download URL is introduced.
- Response contains audit metadata only: target user ID, role, action, actor, result, and timestamp.
- Request size remains bounded by the existing 1..100 `limit` clamp.
