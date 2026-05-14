# T20 Premium Billing/Entitlement Persistence Report

작성일: 2026-05-15  
PDCA Phase: Report  
Slice: T20 Premium Billing/Entitlement Persistence

## Summary

T20 replaced the raw premium skeleton grant with lifecycle-aware entitlement handling and a deterministic billing provider boundary. Premium feature gates now require active, non-expired server-side entitlements, provider failure cannot unlock features, duplicate provider subscriptions are idempotent, and successful grants are audit-visible.

## Delivered

- Added `EntitlementStatus`, `EntitlementStore`, and `InMemoryEntitlementStore`.
- Expanded `Entitlement` with status, provider, provider subscription id, grant time, and expiry.
- Added `BillingProvider`, checkout command/result records, and `LocalTestBillingProvider`.
- Updated `PremiumController` to confirm through provider before grant.
- Added response fields for status/provider/subscription/expiry.
- Added premium grant audit entries via moderation audit log.
- Added Flyway schema `V5__premium_entitlements.sql`.
- Hardened `ExperienceControllerTest` signup helper with unique valid usernames for persistent test stores.

## Safety Notes

- No real payment provider is represented or implied.
- Provider failure returns `502` and does not create an active entitlement.
- Existing self-only policy remains enforced for premium grant/check endpoints.
- Expired entitlements are persisted but do not enable features.

## Test Evidence

- `./gradlew.bat :backend:modules:experience:test --tests com.example.discord.experience.InMemoryExperienceServiceTest --rerun-tasks`: PASS
- `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks`: PASS
- `./gradlew.bat test`: PASS

## Commits

- `ce2879f docs: plan T20 premium billing entitlement persistence`
- `c259320 feat: add premium entitlement lifecycle`
- `ce6919a feat: gate premium grants through billing provider`
- `b19d15f feat: audit premium entitlement changes`

## Residual Risks

- `JdbcEntitlementStore` is still not wired; schema and port are ready but runtime store remains in-memory outside future postgres wiring.
- Cancellation/expiry audit actions exist as enum values but lifecycle endpoints are not yet exposed.
- Frontend copy still presents the local provider simulation as a grant action.

## Next Recommended Task

Proceed to T21 Audit/Security Actions Expansion, because premium audit now introduces new action types and T21 is the natural place to unify broader audit search/filter behavior.
