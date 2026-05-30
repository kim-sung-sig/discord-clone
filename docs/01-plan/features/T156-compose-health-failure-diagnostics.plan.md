# T156 Compose Health Failure Diagnostics Plan

Date: 2026-05-20
Slice: T156 Compose Health Failure Diagnostics

## Objective

Make central compose health failures actionable by printing resource, Docker, Compose, and port diagnostics before the script fails.

## Current State

- T153 added `qa/central-compose-health.ps1`.
- The script could report that a resource did not become ready, but it did not print enough state to diagnose port conflicts or unhealthy containers.

## Scope

1. Extend the health contract to require diagnostics.
2. Add Docker container state diagnostics.
3. Add Compose service state diagnostics.
4. Add Windows and Linux port owner diagnostics.
5. Emit diagnostics at each central resource failure point.

## Acceptance Criteria

- Contract fails before diagnostics exist and passes after implementation.
- Health script still passes on healthy central resources.
- Diagnostics include Docker container state.
- Diagnostics include Compose service state.
- Diagnostics include port ownership checks for Windows and Linux.
- T156-touched files pass `git diff --check`.
