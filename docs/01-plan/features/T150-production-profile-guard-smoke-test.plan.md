# T150 Production Profile Guard Smoke Test Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T150 Production Profile Guard Smoke Test

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T145 added a production-like profile guard, but CI did not prove the guard through the real Spring Boot startup path. |
| Solution | Add a `bootRun` smoke that starts with `production` only, expects failure, and asserts the explicit Postgres-required guard message. |
| Operator Effect | A deployment or CI change that accidentally allows production without Postgres is caught before release. |
| Core Value | Production data safety is enforced as executable startup behavior, not only a unit-level rule. |

## Scope

- Add a production profile guard smoke script.
- Add a contract for the smoke script and CI wiring.
- Add a CI job that runs the smoke without a Postgres service.
- Ensure the runtime guard fires before unrelated missing store beans.
- Keep artifacts for failed or successful smoke inspection.

## Out of Scope

- Starting a full production server.
- Validating all production secrets.
- Provisioning or migrating a production database.

## Success Criteria

- `production` without `postgres` fails through `:backend:boot:bootRun`.
- The failure output includes `production-like runtime profiles require postgres`.
- CI workflow includes the contract, smoke, and artifact upload.
- Backend tests still pass after moving the guard to an earlier startup phase.
