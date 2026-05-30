# T125 CSP Rate-limit Dashboard UI Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T125 CSP Rate-limit Dashboard UI

## Executive Summary

| View | Content |
| --- | --- |
| Problem | `rateLimit.limitedTotal` existed in the CSP dashboard payload but was not visible in `/security`. |
| Solution | Add a summary card that renders rate-limited CSP reports for both zero and non-zero states. |
| Operator Effect | Operators can see when CSP spam or noisy clients are being suppressed without inspecting raw API JSON. |
| Core Value | Rate-limit protection becomes observable in the normal security dashboard workflow. |

## Scope

- Add dashboard UI coverage for `rateLimit.limitedTotal`.
- Render the aggregate value in the `/security` summary strip.
- Adjust summary strip layout for four cards.
- Verify focused dashboard tests, full web tests, and Nuxt build.

## Out of Scope

- Showing rate-limited subjects or IPs.
- Alert acknowledgement workflow.
- Trend charting.

## Success Criteria

- Empty state shows `0` rate-limited reports.
- Non-zero state shows the backend-provided count.
- No raw subject/IP data is added to the dashboard.
- Web tests and build pass.
