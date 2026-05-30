# T138 Dashboard Guard Health UI Panel Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T138 Dashboard Guard Health UI Panel

## Data Flow

1. `/security` loads CSP telemetry from `/api/security/csp-telemetry?limit=25`.
2. After telemetry succeeds, the page requests `/api/security/dashboard-guard-health`.
3. Guard health failure does not replace the CSP dashboard with an error state.
4. A small type guard accepts only the expected health shape before rendering status details.

## UI

The panel renders inside the existing dashboard grid with:

| Field | Behavior |
| --- | --- |
| Status | `Ready`, `Local dev open`, or `Fail closed`. |
| Configuration | Shows whether any guard method is configured. |
| Requirement | Shows whether configured guard enforcement is required. |
| Methods | Shows only enabled/disabled labels for backend, JWT, operator token, admin user IDs, admin roles, and admin scopes. |
| Warnings | Renders endpoint warnings, such as required guard missing. |

## Security Review

- No token, JWT secret, operator token value, admin ID list, role list, or scope list is rendered.
- The page forwards the same bearer/operator-token headers already used by CSP telemetry, but the UI displays only aggregate guard metadata.
- Invalid health payloads are ignored instead of rendering unknown object content.
- Guard health read failure is visible but does not hide CSP telemetry needed during incidents.
