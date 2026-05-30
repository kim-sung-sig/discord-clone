# T141 Admin CLI BootRun Smoke CI Gate Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T141 Admin CLI BootRun Smoke CI Gate

## Findings

| Finding | Result |
| --- | --- |
| Existing smoke was Windows-oriented | The script used `gradlew.bat`, which would fail in Ubuntu CI. |
| Existing smoke assumed prepared database state | A fresh CI database needs Flyway migration before the smoke user can be seeded. |
| Existing smoke passed DB password in process args | The password is now provided through environment variables instead. |

## Security Review

The completed CI gate improves privileged tooling safety because the admin CLI profile is verified through real startup on every CI run. The script avoids mutating global roles and avoids placing the database password in the bootRun argument string.

## Residual Risk

- Grant/revoke and audit row behavior remain separate follow-up tasks.
- The smoke still seeds a deterministic user in the target database; full isolation is tracked separately as T142.
