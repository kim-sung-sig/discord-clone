# T138 Dashboard Guard Health UI Panel Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T138 Dashboard Guard Health UI Panel

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T120 exposed a secret-safe dashboard guard health endpoint, but operators had to call it outside `/security`. |
| Solution | Render guard health status and configured access methods inside the existing `/security` dashboard. |
| Operator Effect | Operators can see whether the dashboard guard is ready, local-dev-open, or fail-closed while reviewing security telemetry. |
| Core Value | Guard misconfiguration becomes visible in the normal operations surface without exposing tokens or secrets. |

## Scope

- Fetch `/api/security/dashboard-guard-health` after CSP telemetry loads.
- Show guard status, configured/required flags, enabled method categories, and warnings.
- Keep guard health failure non-blocking for CSP telemetry.
- Add component coverage for the new UI panel.

## Out of Scope

- CI smoke enforcement for fail-closed production guard status.
- Backend auth-check reachability probing.
- Operator token rotation or expiry workflow.

## Success Criteria

- `/security` renders a guard health panel when the health endpoint returns a valid payload.
- The panel shows only secret-safe booleans/status text.
- CSP telemetry still renders if guard health is unavailable or malformed.
- Existing operator token retry flow remains intact.
- Focused web dashboard tests pass.
