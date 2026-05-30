# T118 Global Admin Grant Operations Tool Feedback

## Improvement Tasks Captured

### T121 Admin CLI BootRun Smoke Test

Add an integration/smoke test or documented local QA command that verifies the actual `admin-cli` profile starts with `web-application-type=none`, runs one command, and terminates cleanly.

### T122 Admin Role Runbook

Create a production runbook for discovering a user id, granting `SECURITY_ADMIN`, verifying `/api/users/@me`, and revoking access.

