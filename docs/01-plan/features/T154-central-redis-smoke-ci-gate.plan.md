# T154 Central Redis Smoke CI Gate Plan

Date: 2026-05-20
Slice: T154 Central Redis Smoke CI Gate

## Objective

Promote the central Redis smoke from a local-only script to a repeatable CI gate.

## Current State

- T148 added `qa/central-redis-smoke.ps1`.
- The smoke script verified backend Redis connectivity and web CSP Redis limiter coordination locally.
- The script used `gradlew.bat`, so it needed Linux-compatible Gradle invocation before running in GitHub Actions.

## Scope

1. Extend the CI workflow contract to require a central Redis smoke job.
2. Add a GitHub Actions job that installs Java, Node, npm dependencies, and runs the Redis smoke.
3. Make `qa/central-redis-smoke.ps1` select `gradlew.bat` on Windows and `gradlew` elsewhere.
4. Ensure native command failures fail the PowerShell script.

## Acceptance Criteria

- CI workflow contract fails before the job exists and passes after implementation.
- `qa-central-redis` job runs central Redis smoke contracts.
- `qa-central-redis` job runs `qa/central-redis-smoke.ps1`.
- Local Redis smoke still passes after script portability changes.
- T154-touched files pass `git diff --check`.
