# T141 Admin CLI BootRun Smoke CI Gate Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T141 Admin CLI BootRun Smoke CI Gate

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T121 proved the admin CLI bootRun path locally, but the smoke was not yet a repeatable CI gate. |
| Solution | Promote the smoke to a dedicated CI job and make the script portable across Windows and Linux runners. |
| Operator Effect | CI catches admin CLI profile startup regressions before privileged role tooling reaches operators. |
| Core Value | Global admin role operations remain backed by a real Spring Boot startup proof. |

## Scope

- Add an admin CLI CI job.
- Make the smoke choose `gradlew` or `gradlew.bat` by platform.
- Ensure Docker Compose can start or reuse `postgres-source`.
- Support fresh database migration before seeding the smoke user.
- Keep bootRun logs as CI artifacts.

## Out of Scope

- Grant/revoke mutation smoke coverage.
- Dedicated isolated database lifecycle ownership beyond the smoke's temporary/fresh DB support.
- Production operator runbook changes.

## Success Criteria

- CI runs the admin CLI smoke contract and smoke.
- The smoke passes on the existing local database.
- The smoke passes on a fresh database that needs Flyway migration first.
- DB credentials are not passed through `bootRun --args`.
