# T125 CSP Rate-limit Dashboard UI Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T125 CSP Rate-limit Dashboard UI

## Findings

| Finding | Result |
| --- | --- |
| API payload already had `rateLimit.limitedTotal` | No server contract change was required. |
| `/security` omitted the value | Added a summary card with `data-testid="csp-rate-limit-limited"`. |
| Layout needed one grid adjustment | Desktop summary strip now uses four equal columns; mobile remains one column. |

## Security Review

The completed UI exposes only the aggregate number of limited reports. It does not reveal client IPs, normalized subjects, report bodies, or operator token state.

## Residual Risk

- Operators still cannot acknowledge CSP alerts; tracked as T127.
- Rate-limit lifecycle metrics remain separate from the dashboard counter; tracked as T131.
