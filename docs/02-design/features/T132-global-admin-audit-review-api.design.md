# T132 Global Admin Audit Review API Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T132 Global Admin Audit Review API

## API

`GET /api/admin/global-roles/audit-log`

| Parameter | Type | Behavior |
| --- | --- | --- |
| `Authorization` | Bearer token | Required. Token user must have `SECURITY_ADMIN`. |
| `targetUserId` | UUID query | Optional target user filter. |
| `limit` | integer query | Bounded to 1..100, default 50. |

Response:

```json
{
  "entries": [
    {
      "targetUserId": "uuid",
      "role": "SECURITY_ADMIN",
      "action": "GRANT",
      "actor": "ops-console",
      "result": "APPLIED",
      "occurredAt": "2026-05-20T00:00:00Z"
    }
  ]
}
```

## Security Review

- Access is based on backend-owned global roles, not client-provided claims.
- Non-admin users receive 403.
- Response contains only audit metadata already captured by T119.
- Limit is bounded to avoid unbounded audit scans.
