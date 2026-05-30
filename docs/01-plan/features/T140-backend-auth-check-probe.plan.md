# T140 Backend Auth Check Probe Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T140 Backend Auth Check Probe

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Dashboard guard health showed backend auth configuration but did not prove the backend auth endpoint was reachable. |
| Solution | Add a secret-safe backend auth probe to the guard health endpoint and `/security` UI. |
| Operator Effect | Operators can distinguish configured backend auth from reachable backend auth. |
| Core Value | Dashboard access readiness becomes operationally verifiable without exposing tokens or backend URLs. |

## Scope

- Add a backend auth check probe helper.
- Treat `200`, `401`, and `403` as reachable auth endpoint responses.
- Return only configured/reachable/status/checkedAt metadata.
- Render backend probe status in `/security`.

## Out of Scope

- Authenticating the probe as a real admin user.
- Exposing backend URL, tokens, response body, or error messages.
- Alerting on backend auth probe failure.

## Success Criteria

- Guard health probe reports reachable status for expected auth responses.
- Probe failures do not leak error details.
- `/security` renders backend auth probe status.
- Focused and full web tests, build, and whitespace checks pass.
