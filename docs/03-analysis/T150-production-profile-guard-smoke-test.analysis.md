# T150 Production Profile Guard Smoke Test Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T150 Production Profile Guard Smoke Test

## Findings

| Finding | Result |
| --- | --- |
| Existing component guard was too late in startup | The first real `bootRun` smoke failed on missing `AuthStore`, not the explicit profile guard. |
| Spring Boot environment post-processing is the correct phase | Moving the runtime profile validation to an `EnvironmentPostProcessor` makes the production/profile invariant fail before bean creation. |
| Spring Boot 3.5 registration path | `EnvironmentPostProcessor` is loaded through `META-INF/spring.factories`, so the registration is tested directly. |

## Security Review

The completed path is safer than the pre-T150 state because production-like startup cannot progress to ambiguous dependency errors when `postgres` is absent. Operators receive a stable, intentional failure reason instead of a generic missing bean failure.

## Residual Risk

- The smoke proves missing `postgres` profile handling, not full production secret completeness.
- Artifact logs should remain non-secret because the smoke does not inject credentials.
