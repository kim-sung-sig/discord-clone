# T168 Privacy-Reviewed Subject Distribution Summary Design

Date: 2026-05-30
Status: Implemented

## Design

The existing CSP rate-limit telemetry store already hashes subjects before storing them. T168 reuses that boundary and only groups by the stored hash internally. The public dashboard payload receives counts by rank, not hashes:

- `limitedTotal`: total rate-limited CSP reports.
- `subjectDistribution.uniqueSubjects`: number of distinct stored subject hashes.
- `subjectDistribution.topSubjects`: up to five ranked buckets with `rank`, `count`, and `share`.

The UI renders the ranked buckets as `Subject #1`, `Subject #2`, and so on. It never renders raw IPs, forwarded headers, or subject hash prefixes for this aggregate panel. Existing single-request subject diagnostics remain separate and continue to expose only the already-reviewed hash prefix diagnostic.

## Privacy Boundary

- Raw subjects are accepted only by the rate limiter and immediately hashed by telemetry storage.
- PostgreSQL aggregation groups by `subject_hash` but returns only counts.
- In-memory aggregation groups by stored `subjectHash` but returns only rank/count/share.
- The dashboard payload has no stable identifier that lets an operator track a subject across time windows.

## Tests

- Payload test verifies aggregate distribution counts and rejects raw IP exposure.
- Dashboard component test verifies ranked labels and rejects raw IP/hash rendering.
- PostgreSQL test expectation covers pruned unique-subject distribution when the integration gate is enabled.
