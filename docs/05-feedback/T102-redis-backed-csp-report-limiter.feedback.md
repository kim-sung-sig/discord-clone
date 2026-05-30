# T102 Redis-backed CSP Report Limiter Feedback

## Improvement Tasks Captured

### T116 Redis-backed CSP Limiter Integration Test

Add a Testcontainers or Docker-backed Redis integration test for the Nuxt CSP report limiter. Current tests verify Redis behavior through a fake client and should be backed by a real Redis smoke test before production rollout.

### T117 Redis Client Lifecycle Cleanup

Add explicit Redis client shutdown handling for Nuxt server lifecycle so the CSP limiter can close connections cleanly during local dev restarts and production termination.

