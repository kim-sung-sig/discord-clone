# T161 Redis CLI Secret Handling In QA Health Checks Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T161 Redis CLI Secret Handling In QA Health Checks

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Redis QA health checks used `redis-cli -a`, which can expose the password in command arguments and warnings. |
| Solution | Switch Redis CLI calls to `REDISCLI_AUTH` and enforce the pattern through contracts. |
| Operator Effect | Local/CI Redis smokes keep using authenticated Redis without putting the password in the CLI argument list. |
| Core Value | QA automation avoids an avoidable secret exposure pattern. |

## Scope

- Update central Redis smoke script.
- Update central Compose health script.
- Update Docker Compose Redis healthcheck.
- Strengthen contracts to reject `redis-cli -a`.

## Out of Scope

- Changing the local development Redis password.
- Rotating CI secrets.
- Reworking Redis URL based app tests.

## Success Criteria

- Contracts fail before scripts use `REDISCLI_AUTH`.
- Contracts pass after the change.
- Central Compose health check passes.
- Central Redis smoke passes.
