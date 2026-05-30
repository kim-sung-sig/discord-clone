# T120 Dashboard Guard Health Endpoint Design

Date: 2026-05-19
Slice: T120 Dashboard Guard Health Endpoint

## Architecture

The health check lives beside the existing dashboard access guard. It reuses `createSecurityDashboardAccessConfig` but converts the config into a sanitized status payload instead of attempting to authorize a dashboard read.

## Health Payload

The payload includes:

- `configured`
- `requireConfiguredGuard`
- `status`
  - `ready`
  - `local-dev-open`
  - `fail-closed`
- `methods`
  - `backend`
  - `jwt`
  - `operatorToken`
  - `adminUserIds`
  - `adminRoles`
  - `adminScopes`
- `warnings`

No token, JWT secret, configured URL, allowlisted user id, role, or scope value is returned.

## Endpoint

`GET /api/security/dashboard-guard-health`

The endpoint returns the sanitized health payload. If the status is `fail-closed`, it sets HTTP `503` so deployment and smoke checks can fail clearly.

## Testing

Unit tests verify:

- production without guard returns `fail-closed`;
- production with JWT/operator-token/role config returns `ready`;
- serialized payload does not contain the configured operator token or JWT secret.
