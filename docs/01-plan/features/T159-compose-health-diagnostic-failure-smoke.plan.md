# T159 Compose Health Diagnostic Failure Smoke Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T159 Compose Health Diagnostic Failure Smoke

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T156 added diagnostics on central Compose health failures, but the failure path itself was not exercised by a fast controlled smoke. |
| Solution | Add a diagnostic-only forced failure mode and a QA smoke that asserts the diagnostic output markers. |
| Operator Effect | CI can prove diagnostic output remains available without breaking real Docker resources. |
| Core Value | Failure diagnostics become tested behavior, not only best-effort code. |

## Scope

- Add controlled diagnostic smoke mode to `qa/central-compose-health.ps1`.
- Add `qa/central-compose-health-diagnostics-smoke.ps1`.
- Add a contract for the diagnostic smoke.
- Wire the contract into CI workflow validation.
- Verify the normal central Compose health path still passes.

## Out of Scope

- Stopping or corrupting real Compose services.
- Changing central resource topology.
- Changing Redis/Postgres/Kafka readiness semantics.

## Success Criteria

- Diagnostic smoke forces the health script to fail quickly.
- Diagnostic output includes stable markers for resource and port context.
- Normal health script still emits `CENTRAL_COMPOSE_HEALTH_PASS`.
- CI workflow contract requires the diagnostic smoke contract.
