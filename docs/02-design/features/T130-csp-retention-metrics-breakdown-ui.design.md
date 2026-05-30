# T130 CSP Retention Metrics Breakdown UI Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T130 CSP Retention Metrics Breakdown UI

## Design

The dashboard payload already includes:

| Field | Meaning |
| --- | --- |
| `retention.discardedTotal` | Total CSP telemetry rows discarded by retention. |
| `retention.discardedByAge` | Rows discarded because they exceeded the retention age. |
| `retention.discardedByMaxEntries` | Rows discarded because the retained entry cap was exceeded. |

T130 keeps the existing summary card and adds a compact definition-list breakdown under the total.

## UI Contract

- `data-testid="csp-retention-discarded"` keeps the total count contract.
- `data-testid="csp-retention-discarded-by-age"` exposes the age-pruned count.
- `data-testid="csp-retention-discarded-by-max-entries"` exposes the max-entry-pruned count.
- Missing retention payloads fall back to `0` for backward-compatible rendering.

## Security Review

- The UI renders only numeric aggregate counters.
- No raw CSP report body, request ID, IP-derived subject, or URL beyond existing recent sanitized telemetry is introduced.
- Vue interpolation is used for all displayed values.
- No `v-html`, dynamic script, unsafe URL binding, or secret-bearing storage change is added.
