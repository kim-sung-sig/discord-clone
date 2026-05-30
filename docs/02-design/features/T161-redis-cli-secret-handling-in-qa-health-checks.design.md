# T161 Redis CLI Secret Handling In QA Health Checks Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T161 Redis CLI Secret Handling In QA Health Checks

## Pattern

Before:

```text
redis-cli -a <password> ping
```

After:

```text
REDISCLI_AUTH=<password> redis-cli ping
```

For Docker:

```text
docker exec -e REDISCLI_AUTH=<password> ms-redis redis-cli ping
```

For Compose:

```text
docker compose exec -T -e REDISCLI_AUTH=<password> ms-redis redis-cli ping
```

## Files

- `qa/central-redis-smoke.ps1`
- `qa/central-compose-health.ps1`
- `infra/docker/docker-compose.yml`
- `qa/central-redis-smoke.contract.ps1`
- `qa/central-compose-health.contract.ps1`
- `qa/central-runtime-resources.contract.ps1`

## Security Review

`REDISCLI_AUTH` still places the password in the child process environment, so it is not a substitute for secret isolation. It is better than command-line `-a` because normal process argument listings and Redis CLI warnings do not include the password.
