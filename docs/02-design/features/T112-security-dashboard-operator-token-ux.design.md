---
slug: T112-security-dashboard-operator-token-ux
ticket: T112
phase: design
hub: "[[T112-security-dashboard-operator-token-ux]]"
---
> 🧭 [[T112-security-dashboard-operator-token-ux]] · PDCA: [[T112-security-dashboard-operator-token-ux.plan]] → **design** → [[T112-security-dashboard-operator-token-ux.analysis]] → [[T112-security-dashboard-operator-token-ux.report]] → [[T112-security-dashboard-operator-token-ux.feedback]]

# T112 Security Dashboard Operator Token UX Design

## UI Contract

The security dashboard includes a compact operator token form below the page header.

- `Apply`: trims and stores the token in session storage, then reloads telemetry.
- `Clear`: removes the stored token and reloads telemetry.
- The token field uses `type="password"` and browser autocomplete is disabled.

## Storage Contract

The existing session storage key is retained:

`dc_security_dashboard_operator_token`

This keeps T105 request forwarding behavior compatible.

## Error Flow

When telemetry loading fails, the error message and token form remain visible so operators can recover without opening the browser console.
