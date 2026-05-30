# T139 Dashboard Guard Health Smoke Check Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T139 Dashboard Guard Health Smoke Check

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T120/T138 made dashboard guard health visible, but deployment automation did not fail when production guard health was `fail-closed`. |
| Solution | Add a Nuxt production smoke that calls `/api/security/dashboard-guard-health` and fails on non-ready or fail-closed status. |
| Operator Effect | CI and deployment runs can catch missing dashboard guard configuration before release. |
| Core Value | Security dashboard access control becomes both observable and automatically enforced. |

## Scope

- Add a PowerShell smoke that builds/starts the Nuxt production server.
- Call `/api/security/dashboard-guard-health` over HTTP.
- Fail when the endpoint returns HTTP 503 or `status: fail-closed`.
- Assert ready production guard state when a guard token is configured.
- Add CI job wiring and contract coverage.

## Out of Scope

- Backend auth-check reachability probing.
- Production secret provisioning policy.
- Operator token rotation or expiry workflow.

## Success Criteria

- Contract script fails before the smoke exists.
- Smoke passes with a configured production guard token.
- Smoke fails without a configured production guard.
- CI includes a dedicated dashboard guard health job and artifact upload.
- Guard health artifacts do not expose the configured token value.
