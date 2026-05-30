# T121 Admin CLI BootRun Smoke Test Feedback

Date: 2026-05-19
Slice: T121 Admin CLI BootRun Smoke Test

## Improvement Tasks Captured

### T141 Admin CLI BootRun Smoke CI Gate

Run the T121 smoke in CI or a dedicated QA workflow when Docker and Postgres are available.

### T142 Admin CLI Smoke Database Isolation

Move smoke setup to a temporary database or cleanup lifecycle so central development data remains tidy.

### T143 Admin CLI Grant/Revoke BootRun Smoke

Extend the bootRun smoke beyond `list` to verify `grant` and `revoke` commands with audit rows.

## Loop Decision

T121 scored 28/30 and passed the threshold. Continue to T122 unless CI gating should be promoted immediately.
