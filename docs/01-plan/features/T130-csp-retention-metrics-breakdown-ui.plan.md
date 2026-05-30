# T130 CSP Retention Metrics Breakdown UI Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T130 CSP Retention Metrics Breakdown UI

## Executive Summary

| View | Content |
| --- | --- |
| Problem | `/security` showed only the total number of CSP telemetry records discarded by retention. |
| Solution | Render the existing `discardedByAge` and `discardedByMaxEntries` counters next to the total. |
| Operator Effect | Operators can tell whether retained telemetry was pruned by age or by capacity limits. |
| Core Value | Retention behavior becomes more actionable without exposing raw CSP reports or subjects. |

## Scope

- Add UI coverage for CSP retention breakdown counters.
- Render age-pruned and max-entry-pruned counts in `/security`.
- Keep the display aggregate-only and secret-safe.
- Update tasking docs after implementation.

## Out of Scope

- Changing the retention store contract.
- Adding trend charts or export flows.
- Exposing raw discarded CSP telemetry.

## Success Criteria

- `/security` renders total discarded records plus age and max-entry breakdown counts.
- The UI remains aggregate-only and does not render request IDs or raw report origins for discarded entries.
- Focused dashboard tests pass.
- Related security dashboard tests, build, and whitespace checks pass.
