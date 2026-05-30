# T131 Redis CSP Limiter Lifecycle Metrics Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T131 Redis CSP Limiter Lifecycle Metrics

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Redis-backed CSP report limiting failed closed, but operators could not see connection churn or fail-closed counts from `/security`. |
| Solution | Add secret-safe lifecycle metrics to the Redis limiter and surface them through the CSP dashboard payload and UI. |
| Operator Effect | Operators can detect Redis connection failures, reconnect churn, close calls, and limiter fail-closed decisions. |
| Core Value | CSP abuse protection becomes observable without exposing Redis credentials or rate-limit subjects. |

## Scope

- Add optional lifecycle metrics to the CSP report rate-limiter contract.
- Track Redis client connect, failure, error, reconnect, and close counters.
- Track Redis limiter fail-closed decisions.
- Include lifecycle metrics in the guarded CSP dashboard payload.
- Render lifecycle metrics in `/security`.

## Out of Scope

- Alerting thresholds for Redis lifecycle churn.
- Persisting lifecycle metrics across process restarts.
- Exposing Redis URL, password, raw IP, raw subject, or raw Redis keys.

## Success Criteria

- Redis limiter exposes fail-closed counts.
- Node Redis client exposes aggregate lifecycle counters and recent event timestamps.
- Dashboard payload includes lifecycle metrics when a limiter exposes them.
- `/security` renders lifecycle counts without secrets.
- Focused tests, related web tests, full web tests, build, and whitespace checks pass.
