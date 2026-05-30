# T134 Duplicate-Safe Grant Audit Result Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T134 Duplicate-Safe Grant Audit Result

## Findings

- Revoke already distinguished applied and no-op outcomes.
- Grant used `ON CONFLICT DO NOTHING` in Postgres and set semantics in memory, but the result was discarded.
- The CLI always recorded grant audit entries as `APPLIED`, even when no new role was inserted.

## Risk Review

| Risk | Control |
| --- | --- |
| Duplicate grant appears as new privilege elevation | Record duplicate grant as `NOOP`. |
| Existing callers break | Java callers may ignore the returned boolean; current direct test callers continue to work. |
| Store behavior diverges | Add tests for CLI audit and Postgres duplicate return value. |

## Remaining Gaps

- Audit review UI still does not highlight `NOOP` entries separately.
