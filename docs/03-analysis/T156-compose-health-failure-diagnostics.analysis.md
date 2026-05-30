# T156 Compose Health Failure Diagnostics Analysis

Date: 2026-05-20
Slice: T156 Compose Health Failure Diagnostics

## Findings

- Earlier Redis smoke work hit a real port conflict on `16379`, which confirmed that health failures need immediate port and container context.
- Diagnostics must support Windows local development and Linux CI runners.
- The diagnostics should be best-effort and must not hide the original readiness failure.

## Tradeoffs

- Linux port ownership uses `ss -ltnp`; if the runner image lacks `ss`, the diagnostic block reports that failure and continues.
- Normal healthy runs do not print the diagnostic block.

## Follow-Up

- Add a controlled failure-path test that verifies diagnostic output without waiting for long readiness timeouts.
