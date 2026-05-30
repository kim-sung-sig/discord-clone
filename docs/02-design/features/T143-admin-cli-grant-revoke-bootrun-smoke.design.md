# T143 Admin CLI Grant/Revoke BootRun Smoke Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T143 Admin CLI Grant/Revoke BootRun Smoke

## Design

The smoke now executes a full mutation cycle against the generated fixture:

| Phase | Check |
| --- | --- |
| Initial list | CLI starts through `bootRun`, sees the generated user, and role state is empty. |
| Grant | `grant` runs with `confirm=true` and actor `admin-cli-bootrun-smoke`; output includes `granted SECURITY_ADMIN`. |
| Post-grant state | `user_global_roles` contains `SECURITY_ADMIN`; audit contains `GRANT/APPLIED`. |
| List after grant | CLI output includes `global roles for <id>: SECURITY_ADMIN`. |
| Revoke | `revoke` runs with `confirm=true`; output includes `revoked SECURITY_ADMIN`. |
| Post-revoke state | `user_global_roles` is empty; audit contains `REVOKE/APPLIED`. |
| List after revoke | CLI output includes an empty role list for the generated user. |

Each bootRun phase writes a separate artifact log under `qa/artifacts/admin-cli/local` by default.

## Security Review

- The smoke actor is a fixed non-secret value.
- Database credentials remain environment variables and are not added to Gradle args.
- Audit verification uses aggregate counts only and does not print raw audit row contents.
- T142 cleanup still removes the generated fixture and audit rows in `finally`.
