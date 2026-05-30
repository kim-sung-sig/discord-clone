# T145 Remove Runtime In-Memory Persistence Defaults Analysis

Date: 2026-05-20
Slice: T145 Remove Runtime In-Memory Persistence Defaults

## Implementation Notes

- Added a failing guard test before implementation.
- Added `RuntimeResourceProfileGuard` in the ops package.
- Updated in-memory auth/guild/message/invite persistence profiles to exclude `production` and `admin-cli`.
- Kept the guard focused on Postgres persistence; Redis and Kafka connectivity are tracked by separate tasks.

## Feature Impact

- Local development without explicit profiles can still run with in-memory data.
- Backend tests can still use in-memory stores.
- Admin CLI mutating global admin roles must use `postgres`, protecting audit and role changes from disappearing after process exit.
- Production-like backend starts now fail early when central persistence is missing.

## Remaining Gaps

- There is no full `production` startup smoke test that verifies the guard through `SpringApplication`.
- Redis and Kafka central-resource enforcement remain separate queue items.
