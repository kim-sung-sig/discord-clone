# T150 Production Profile Guard Smoke Test Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T150 Production Profile Guard Smoke Test

## Design

The smoke runs the real Spring Boot Gradle entrypoint with a deliberately invalid production profile set:

```powershell
:backend:boot:bootRun --args=--spring.profiles.active=production --spring.main.web-application-type=none
```

`spring.main.web-application-type=none` keeps the smoke from becoming a long-running server process while preserving Spring Boot context startup behavior.

## Guard Placement

`RuntimeResourceProfileGuard` remains the shared validator. A new `RuntimeResourceProfileEnvironmentPostProcessor` delegates to it before bean creation, registered through `META-INF/spring.factories`.

This prevents unrelated missing persistence beans from masking the intended security failure.

## CI Integration

| Element | Design |
| --- | --- |
| Job | `qa-production-profile-guard` |
| Contract | `qa/production-profile-guard-smoke.contract.ps1` |
| Smoke | `qa/production-profile-guard-smoke.ps1` |
| Artifact path | `qa/artifacts/production-profile-guard` |
| Pass marker | `PRODUCTION_PROFILE_GUARD_SMOKE_PASS` |

## Security Review

- The smoke intentionally runs without database credentials.
- The assertion checks an explicit fail-closed message.
- The artifact is startup output only; no production secrets are injected.
