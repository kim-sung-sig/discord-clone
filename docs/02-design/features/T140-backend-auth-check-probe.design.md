# T140 Backend Auth Check Probe Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T140 Backend Auth Check Probe

## Design

Add `probeSecurityDashboardBackendAuthCheck()` to `security-dashboard-access.ts`.

| Field | Meaning |
| --- | --- |
| `configured` | Whether `NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL` is configured. |
| `reachable` | Whether the configured endpoint responded like an auth endpoint. |
| `statusCode` | HTTP status code, if a response was received. |
| `checkedAt` | Probe timestamp. |

The probe calls the configured URL with `Authorization: Bearer probe`. `200`, `401`, and `403` are reachable because they prove the backend endpoint responded through an auth path.

## UI

`/security` renders the backend probe inside the dashboard guard health panel when configured.

## Security Review

- The response never includes backend URL, configured token, response body, or thrown error text.
- The probe token is a fixed dummy value and is not a secret.
- Failures return only `reachable: false` and `checkedAt`.
