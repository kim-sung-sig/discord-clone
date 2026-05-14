# T20 Premium Billing/Entitlement Persistence Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T20 Premium Billing/Entitlement Persistence

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `./gradlew.bat :backend:modules:experience:test --tests com.example.discord.experience.InMemoryExperienceServiceTest --rerun-tasks` before implementation | RED | lifecycle/store/status types and methods did not exist |
| `./gradlew.bat :backend:modules:experience:test --tests com.example.discord.experience.InMemoryExperienceServiceTest --rerun-tasks` after lifecycle implementation | PASS | active, expired, canceled, duplicate provider subscription tests passed |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks` before billing provider implementation | RED | provider failure did not block grant; status/expiry response was missing |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks` after billing provider implementation | PASS | provider failure, duplicate grant, expiry gate, existing stage/soundboard flows passed |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest.premiumGrantEmitsAuditLogEntry --rerun-tasks` before audit implementation | RED | premium audit log entry was missing |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks` after audit implementation | PASS | premium grant audit log test passed with full experience controller suite |
| `./gradlew.bat test` | PASS | full backend test suite passed; build successful |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Premium gate uses persisted entitlements and expiry | PASS | entitlement store port added; gate checks status and `expiresAt` against service clock |
| Duplicate entitlement grants are idempotent | PASS | provider subscription id lookup returns existing entitlement id |
| Billing provider failures do not unlock features | PASS | `simulateProviderFailure` returns 502 and follow-up gate remains false |
| Entitlement changes emit audit events | PASS | premium grant appends `PREMIUM_ENTITLEMENT_GRANTED` to guild audit log |
| Catalog/shop API does not imply real payment | PASS | `LocalTestBillingProvider` is explicit and deterministic; no real provider branding is used |

## Failure Criteria Review

| Failure Criteria | Status | Evidence |
| --- | --- | --- |
| Client can self-grant premium outside test/provider boundary | NOT OBSERVED | grant now goes through `BillingProvider`; failure path does not persist entitlement |
| Expired entitlement remains active | NOT OBSERVED | expired entitlement test returns `enabled=false` |
| Catalog/shop API implies real payment without provider boundary | NOT OBSERVED | local test provider boundary is explicit |

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| JDBC entitlement store is not wired as a Spring bean yet | Medium | `V5__premium_entitlements.sql` and store port exist; implement `JdbcEntitlementStore` under postgres profile in a later persistence hardening pass |
| Cancellation/expiry audit methods are modeled but not exposed by REST | Low-medium | Add admin lifecycle endpoints when subscription management task expands |
| Frontend still uses skeleton grant button wording | Low | T23 frontend integration should rename local grant action to provider simulation wording |

## Decision

T20 backend slice is acceptable for the current roadmap. It removes raw self-grant semantics, makes premium gates lifecycle-aware, blocks provider failure unlocks, adds audit visibility, and creates the database schema target for persistent entitlements.
