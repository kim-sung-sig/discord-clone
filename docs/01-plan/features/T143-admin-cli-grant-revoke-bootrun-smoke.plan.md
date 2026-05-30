# T143 Admin CLI Grant/Revoke BootRun Smoke Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T143 Admin CLI Grant/Revoke BootRun Smoke

## Executive Summary

| View | Content |
| --- | --- |
| Problem | The admin CLI bootRun smoke proved only the `list` path, leaving real `grant` and `revoke` execution unverified in the central Postgres path. |
| Solution | Extend the smoke to run `grant`, verify role state and audit, run `revoke`, and verify final state and audit. |
| Operator Effect | CI and operators can prove the same Gradle bootRun path used in runbooks performs mutating role operations correctly. |
| Core Value | Admin security operations now have real smoke coverage for both read and write paths. |

## Scope

- Add grant/revoke smoke expectations to the contract.
- Run `grant` with `discord.admin-role.confirm=true` and a smoke actor.
- Verify the role exists after grant and is absent after revoke.
- Verify `GRANT/APPLIED` and `REVOKE/APPLIED` audit rows.
- Capture separate artifacts for initial list, grant, list-after-grant, revoke, and list-after-revoke.
- Keep generated fixture cleanup from T142.

## Out of Scope

- Testing duplicate `NOOP` grant/revoke bootRun paths.
- Calling the HTTP audit-review API.
- Promoting smoke artifacts to permanent evidence storage.

## Success Criteria

- Contract test fails before implementation and passes after implementation.
- PowerShell Core and Windows PowerShell smoke executions pass.
- The generated fixture leaves no rows in shared central Postgres after cleanup.
- Whitespace check reports no errors.
