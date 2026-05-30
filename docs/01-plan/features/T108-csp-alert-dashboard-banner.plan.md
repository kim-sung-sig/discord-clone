# T108 CSP Alert Dashboard Banner Plan

## Objective

Render active CSP alert threshold state in the security dashboard so operators do not need to inspect raw API JSON to notice violation spikes.

## Current State

- T100 computes `dashboard.alert.active` and `dashboard.alert.reasons`.
- `/security` renders CSP totals, directive mix, and recent sanitized reports.
- Active alert state is not yet visible in the UI.

## Scope

1. Add an active-alert banner to `/security`.
2. Show all sanitized alert reasons from the dashboard payload.
3. Keep the banner hidden when alert state is inactive or absent.
4. Style the banner consistently with the current security dashboard layout.

## Acceptance Criteria

- Active alert payload renders a visible `csp-alert-banner`.
- Banner includes a clear active-alert label and threshold reasons.
- Inactive alert payload does not render the banner.
- Focused dashboard tests, full web tests, build, and whitespace checks pass.
