# T62 Gateway Subscription Reconciliation Design

Date: 2026-05-21

## Design

`InMemoryGatewayService` already registers subscriptions during `identify`. T62 extends the same behavior to session
access paths that can happen on a different Gateway node or after permission changes:

- `resume(sessionId, userId, lastSequence)` calls `registerSubscriptions(session)` before replay calculation.
- `poll(sessionId, userId, afterSequence)` calls `registerSubscriptions(session)` before delivery calculation.

`registerSubscriptions` remains idempotent because event buses store or tolerate repeated subscriptions.

## Why This Shape

Redis Gateway event buses subscribe to stream keys locally. A node that did not run the original `identify` may own the
resumed connection but have no stream subscriptions yet. Re-registering on resume fixes that without changing session
metadata or event payloads.

## Security

The change does not store new data. Hidden-channel filtering remains inside `canDeliver`, so subscription reconciliation
does not grant delivery by itself.

## Screenshot Evidence

Screenshot artifacts are produced under:

```text
output/playwright/t62-subscription-reconciliation/
```

The HTML report is:

```text
docs/04-report/T62-subscription-reconciliation-screenshot-report.html
```
