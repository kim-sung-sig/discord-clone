# T161 Redis CLI Secret Handling In QA Health Checks Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T161 Redis CLI Secret Handling In QA Health Checks

## Findings

| Finding | Result |
| --- | --- |
| `qa/central-redis-smoke.ps1` used `redis-cli -a` | Replaced with Docker/Compose `-e REDISCLI_AUTH=...`. |
| `qa/central-compose-health.ps1` used `redis-cli -a` | Replaced with Docker/Compose `-e REDISCLI_AUTH=...`. |
| Compose Redis healthcheck used `redis-cli -a` | Added service env and switched healthcheck to `REDISCLI_AUTH=... redis-cli ping`. |
| Existing contracts did not prevent regression | Contracts now require `REDISCLI_AUTH` and reject `redis-cli -a`. |

## Security Review

The new pattern reduces command-line secret exposure in QA health checks. It does not eliminate all environment-based secret risks, but it aligns with Redis CLI guidance and avoids the explicit password argument.

## Residual Risk

- `NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL` still embeds credentials in a URL for application configuration. That is separate runtime configuration and should be handled through secret management in deployment.
