# T20 Premium Billing/Entitlement Persistence Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T20 Premium Billing/Entitlement Persistence

## Architecture Decision

Split premium into three boundaries while preserving the existing `experience` module shape:

1. Entitlement domain and store decide whether a feature is enabled.
2. Billing provider port represents external checkout/subscription state without implementing real payment.
3. Audit publisher records entitlement lifecycle changes for admin traceability.

The existing REST controller must stop treating a grant as a raw self-write. Instead, the controller calls a billing-backed application method that only grants when the provider returns a successful deterministic confirmation.

## Domain Model

`Entitlement` becomes lifecycle-aware:

- `id`
- `userId`
- `guildId`
- `featureKey`
- `status`
- `provider`
- `providerSubscriptionId`
- `grantedAt`
- `expiresAt`

`enabled` is true only when:

- user id and feature key match
- status is `ACTIVE`
- `expiresAt` is null or after `clock.instant()`

## Store Port

`EntitlementStore`:

- `Entitlement save(Entitlement entitlement)`
- `Optional<Entitlement> findById(UUID id)`
- `Optional<Entitlement> findByProviderSubscription(String provider, String providerSubscriptionId)`
- `List<Entitlement> findByUserAndFeature(UUID userId, String featureKey)`
- `List<Entitlement> findByGuild(UUID guildId)`

Default implementation remains in-memory. A Flyway schema will define `premium_entitlements` so postgres profile has a persistent target even if wiring is incremental.

## Billing Provider Port

`BillingProvider`:

- `BillingCheckoutResult confirm(BillingCheckoutCommand command)`

`LocalTestBillingProvider` returns success only for explicit local/test requests and never claims real payment. Failure responses include a reason and must not write active entitlements.

## Audit Boundary

Add premium audit action values:

- `PREMIUM_ENTITLEMENT_GRANTED`
- `PREMIUM_ENTITLEMENT_CANCELED`
- `PREMIUM_ENTITLEMENT_EXPIRED`

Prefer reusing `InMemoryModerationService` audit log if the method can be safely exposed as an append-only port. If that creates coupling, create `PremiumAuditPublisher` in boot with an in-memory event list and document T21 migration into unified audit search.

## API Compatibility

Existing endpoint remains for local skeleton compatibility:

- `POST /api/premium/users/{userId}/entitlements`

Request body expands:

- `guildId`
- `featureKey`
- `providerSubscriptionId`
- optional `expiresAt`
- optional `simulateProviderFailure`

Responses include status and expiry. Existing clients that omit provider fields use a safe local provider subscription id derived from user/guild/feature for deterministic local tests.

## Test Strategy

Backend module tests:

- active entitlement enables feature.
- expired entitlement disables feature.
- canceled/past-due entitlement disables feature.
- duplicate provider subscription is idempotent.

Boot MockMvc tests:

- provider failure returns an error and feature remains locked.
- duplicate grant returns same entitlement id.
- audit log contains premium grant entry.
- self-only policy remains enforced.

## Risks

- Full JDBC wiring may exceed this slice if existing premium service remains in-memory-only. In that case, schema and store port should still be committed so T21/T23 can wire production profile incrementally.
- Existing frontend skeleton button says grant; wording may need update to avoid implying real payment.
