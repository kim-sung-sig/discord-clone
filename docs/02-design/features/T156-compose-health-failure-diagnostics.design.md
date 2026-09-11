---
slug: T156-compose-health-failure-diagnostics
ticket: T156
phase: design
hub: "[[T156-compose-health-failure-diagnostics]]"
---
> 🧭 [[T156-compose-health-failure-diagnostics]] · PDCA: [[T156-compose-health-failure-diagnostics.plan]] → **design** → [[T156-compose-health-failure-diagnostics.analysis]] → [[T156-compose-health-failure-diagnostics.report]] → [[T156-compose-health-failure-diagnostics.feedback]]

# T156 Compose Health Failure Diagnostics Design

Date: 2026-05-20
Slice: T156 Compose Health Failure Diagnostics

## Design

`qa/central-compose-health.ps1` now includes `Write-HealthDiagnostics(resource, port)`.

## Diagnostic Output

The diagnostic block prints:

- Resource name and expected port.
- `docker ps -a` with container names, status, and ports.
- `docker compose -f $compose ps` for this repository's Compose project.
- Windows port owners through `Get-NetTCPConnection`.
- Linux port listeners through `ss -ltnp`.

## Failure Points

Diagnostics are emitted when:

- Compose startup for a resource fails.
- Postgres does not become ready.
- Redis does not become ready.
- Kafka port does not become ready.
