# T125 CSP Rate-limit Dashboard UI Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T125 CSP Rate-limit Dashboard UI

## Design

Add a fourth summary card to `/security`:

| Label | Value |
| --- | --- |
| Rate-limited reports | `dashboard.rateLimit?.limitedTotal ?? 0` |

The card sits next to total reports, directive groups, and retention discards. The layout changes from three to four equal columns on desktop and keeps the existing single-column mobile behavior.

## Test Strategy

Extend `security-dashboard.test.ts` with one focused test that:

- Renders `0` when `limitedTotal` is empty.
- Reloads dashboard data.
- Renders a non-zero value when the API returns one.

## Security Review

Only an aggregate counter is exposed. The UI does not show rate-limit subjects, raw IPs, headers, or report payloads.
