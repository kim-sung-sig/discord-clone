# T139 Dashboard Guard Health Smoke Check Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T139 Dashboard Guard Health Smoke Check

## Smoke Command

`qa/dashboard-guard-health-smoke.ps1`

| Step | Behavior |
| --- | --- |
| Build | Runs `npm run build --workspace @discord-clone/web` unless `-SkipBuild` is provided. |
| Server | Starts `apps/web/.output/server/index.mjs` with `NODE_ENV=production`, local host, and a configurable port. |
| Probe | Calls `/api/security/dashboard-guard-health` using `Invoke-WebRequest`. |
| Pass | Requires HTTP 200, `status: ready`, `configured: true`, and `requireConfiguredGuard: true`. |
| Fail | Throws if HTTP status is non-200, if status is `fail-closed`, or if the response exposes the configured token. |

## CI

Job: `qa-dashboard-guard-health`

- Installs Node 22 dependencies.
- Runs `qa/dashboard-guard-health-smoke.contract.ps1`.
- Runs `qa/dashboard-guard-health-smoke.ps1` with a CI-only dummy `NUXT_SECURITY_DASHBOARD_TOKEN`.
- Uploads `qa/artifacts/dashboard-guard-health`.

## Security Review

- The token is passed through environment variables, not command-line arguments.
- Artifacts store `operator_token_configured=true/false`, not the token value.
- The smoke asserts the endpoint response does not contain `NUXT_SECURITY_DASHBOARD_TOKEN`.
- The endpoint payload remains aggregate guard metadata only.
