# T112 Security Dashboard Operator Token UX Plan

## Objective

Let operators enter and clear the security dashboard operator token from the dashboard UI instead of editing browser storage manually.

## Current State

- T105 allows `x-operator-token` as a break-glass dashboard guard.
- `/security` forwards a token only if `dc_security_dashboard_operator_token` already exists in session storage.
- Operators have no in-app control for setting or retrying the token.

## Scope

1. Add an operator token form to `/security`.
2. Persist the entered token in session storage.
3. Retry dashboard telemetry loading after applying the token.
4. Allow clearing the saved token.

## Acceptance Criteria

- A failed dashboard load still exposes the token form.
- Submitting a token stores it in session storage.
- The retry request includes `x-operator-token`.
- The dashboard renders telemetry after the retry succeeds.
- Focused tests, related web tests, full web tests, build, and whitespace checks pass.
