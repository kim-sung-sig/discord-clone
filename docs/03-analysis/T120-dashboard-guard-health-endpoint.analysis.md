# T120 Dashboard Guard Health Endpoint Analysis

Date: 2026-05-19
Slice: T120 Dashboard Guard Health Endpoint

## Analysis

T113 introduced the correct production safety behavior: an unconfigured dashboard guard fails closed. That creates an operator visibility gap because the telemetry API itself returns only `403` when guard checks fail. T120 solves this by adding a small health model that explains whether guard enforcement is configured without exposing the sensitive configuration.

The implementation stays within the existing `security-dashboard-access` boundary. This avoids duplicating environment parsing and keeps future guard methods visible through one health function.

## Trade-Offs

- The health endpoint is intentionally diagnostic and does not perform a backend auth-check probe.
- It exposes which guard method categories are enabled, but not their values.
- It does not yet render in the `/security` UI.

## Security Notes

- Operator token values are never returned.
- JWT secrets are never returned.
- Allowlisted user ids, roles, and scopes are represented only as booleans.
- A fail-closed state returns HTTP `503` for deployment smoke checks.

## Residual Risk

Operators still need UI visibility or deployment checks that call the health endpoint. Those are registered as follow-up tasks.
