# T20 Premium Billing/Entitlement Persistence Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T20 Premium Billing/Entitlement Persistence

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T14 premium grant는 in-memory test skeleton이라 expiry, lifecycle, idempotency, provider failure boundary, audit trace가 없다. |
| Solution | entitlement를 저장소 포트 뒤의 persisted-capable 모델로 확장하고, billing provider port skeleton과 subscription lifecycle state를 추가한다. |
| Function UX Effect | premium gate는 유효기간과 상태를 반영해 unlock/lock을 결정하고, 중복 grant는 같은 entitlement로 수렴한다. |
| Core Value | 실제 결제를 흉내 내지 않고 provider boundary와 audit 가능성을 먼저 확보해 client self-grant와 expired entitlement 리스크를 제거한다. |

## Scope

- Entitlement domain expansion:
  - status: `ACTIVE`, `CANCELED`, `EXPIRED`, `PAST_DUE`
  - `grantedAt`, `expiresAt`, `provider`, `providerSubscriptionId`
  - active gate requires status `ACTIVE` and `expiresAt > now` when expiry exists
- Store boundary:
  - `EntitlementStore` port
  - in-memory implementation for tests/dev
  - JDBC/Flyway schema design for postgres profile if feasible in this slice
- Billing provider boundary:
  - `BillingProvider` interface
  - deterministic local/test provider
  - failure result never unlocks a feature
- Idempotency:
  - duplicate grants for same user/guild/feature/providerSubscriptionId return existing active entitlement
- Audit:
  - premium entitlement grant/cancel/expire skeleton emits audit entry through moderation audit log path or a dedicated premium audit port

## Out of Scope

- Real Stripe/Toss/PayPal integration.
- Tax, refund, chargeback, invoice, or fraud workflows.
- Client-side checkout UI beyond existing skeleton compatibility.
- Real background scheduler for expiry sweeps unless the domain method is trivial and testable.

## Success Criteria

- Premium gate uses server-side entitlement status and expiry.
- Duplicate entitlement grants are idempotent.
- Billing provider failures do not unlock features.
- Entitlement lifecycle changes emit audit-visible events.
- Existing frontend skeleton tests remain compatible or are updated to reflect safe test provider wording.

## Failure Criteria

- Client can self-grant premium outside the explicit local/test provider boundary.
- Expired entitlement remains active.
- Catalog/shop API implies real payment without a provider boundary.
- Provider failure creates an active entitlement.
