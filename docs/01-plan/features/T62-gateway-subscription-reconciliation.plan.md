# T62 Gateway Subscription Reconciliation Plan

Date: 2026-05-21

## Goal

Ensure Gateway nodes re-register current guild and channel subscriptions when sessions resume or permissions change after
identify.

## Scope

- Reconcile subscriptions on `resume`.
- Reconcile subscriptions on `poll` so newly visible channels are subscribed before later Redis stream polling.
- Preserve delivery-time hidden-channel filtering.
- Prove behavior with focused Gateway service tests and central Redis fanout smoke.
- Produce a screenshot-based report using the Desktop Discord HTML template style.

## Acceptance

- RED first: resume on a different node does not subscribe visible channels.
- RED first: poll after a permission change does not subscribe newly visible channels.
- GREEN: resume and poll both call subscription registration for the active session.
- Screenshot artifacts and HTML report are generated.
