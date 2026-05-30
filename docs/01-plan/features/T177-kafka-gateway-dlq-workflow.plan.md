# T177 Kafka Gateway DLQ Workflow Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T177 Kafka Gateway DLQ retention, alert, and replay workflow

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Kafka Gateway dead-letter records exist, but operators do not have a documented retention, alert, replay, or discard process. |
| Solution | Add a contract-checked DLQ runbook and expose default DLQ retention/alert policy values in Kafka configuration. |
| Operator Effect | Operators can triage DLQ records without copying raw payloads or replaying unsafe events. |
| Core Value | The T152 dead-letter mechanism becomes operationally usable before production reliance. |

## Scope

- Add a Kafka Gateway DLQ runbook.
- Define default DLQ retention as 168 hours.
- Define production alert threshold as any DLQ record count greater than 0.
- Define reviewed replay, discard, and hold workflows.
- Add a contract test for the runbook and policy defaults.
- Wire the contract into CI workflow checks.

## Out of Scope

- Automated DLQ metrics collection.
- A replay service or admin UI.
- Broker-specific topic creation automation.

## Success Criteria

- Runbook contract fails before the runbook exists and passes after implementation.
- CI workflow contract requires the DLQ runbook contract.
- Kafka profile and `.env.example` expose DLQ policy defaults.
- Security rules prohibit raw payload/token/signed URL copying.
