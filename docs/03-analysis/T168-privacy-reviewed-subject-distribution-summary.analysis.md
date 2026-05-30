# T168 Privacy-Reviewed Subject Distribution Summary Analysis

Date: 2026-05-30

## Gap Review

Before T168, operators could see total CSP rate-limit events and a single request's subject diagnostics, but not whether rate limiting was concentrated on one subject or spread across many subjects. Showing raw IPs or stable hashes would create a privacy and tracking risk.

## Implementation Match

- Aggregate distribution is computed from already-hashed stored subjects.
- Dashboard payload returns only `uniqueSubjects` and ranked count/share buckets.
- `/security` renders `Subject #N` labels rather than identifiers.
- Tests assert that raw IP ranges and hash-like fixture values do not appear in rendered distribution output.

## Residual Risk

- PostgreSQL distribution verification remains skipped unless `NUXT_RUN_POSTGRES_TESTS=true`.
- The distribution is intentionally low-detail; deeper investigation still requires controlled backend/log access outside the dashboard.
