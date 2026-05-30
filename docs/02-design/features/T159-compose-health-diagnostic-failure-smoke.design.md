# T159 Compose Health Diagnostic Failure Smoke Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T159 Compose Health Diagnostic Failure Smoke

## Design

Use an environment-gated forced failure path inside `qa/central-compose-health.ps1`.

| Element | Design |
| --- | --- |
| Trigger | `CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE=true` |
| Resource name | `diagnostic-smoke` |
| Port | `1` |
| Failure marker | `CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE_FAILURE` |
| Pass marker | `CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS_SMOKE_PASS` |

The diagnostic smoke runs the health script in a child PowerShell process so stdout/stderr and the non-zero exit code can be captured without terminating the wrapper.

## Risk Controls

- The forced mode runs before any resource startup logic.
- The forced mode does not stop, remove, or mutate containers.
- The existing normal path remains unchanged except for the guarded diagnostic branch.

## CI Integration

- Add `qa/central-compose-health-diagnostics-smoke.contract.ps1` to the central Redis contract step.
- Keep the full diagnostic smoke as a local/QA command; the contract proves wiring and marker requirements, while the smoke can be run in CI later if runtime cost is acceptable.
