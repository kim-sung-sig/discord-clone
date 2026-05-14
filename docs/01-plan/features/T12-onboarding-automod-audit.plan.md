# T12 Onboarding/AutoMod/Audit Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T12 Onboarding/AutoMod/Audit

## Problem

| 관점 | 내용 |
| --- | --- |
| User Problem | 신규 멤버가 서버 온보딩을 통해 역할/채널을 자동 배정받을 수 없고, 위험 메시지가 저장 전에 차단되지 않으며, 관리자 조치의 감사 이력이 없다. |
| Product Problem | Community server 운영 기능의 최소 안전장치가 없어 Discord clone의 운영/보안 축이 비어 있다. |
| Engineering Problem | write API마다 독립적으로 권한 검사는 있으나 moderation/audit cross-cutting boundary가 없다. |
| Core Value | T13 이후 moderation/reporting/security action으로 확장할 수 있는 AutoMod pre-persist gate와 audit trail 기반을 만든다. |

## Scope

- Onboarding question/answer skeleton.
- Answer submission that assigns configured role IDs to the member.
- AutoMod keyword/spam rule skeleton.
- Message create path pre-persist AutoMod block.
- Audit log creation for admin actions and AutoMod blocks.
- Nuxt moderation panel showing onboarding, AutoMod, and audit state.

## Out of Scope

- ML/LLM moderation, safety classifiers, and trust scoring.
- Full Discord onboarding UX wizard branching.
- Ban/kick/timeout enforcement.
- Durable audit log database schema.
- Gateway fanout for moderation events.

## Success Criteria

- AutoMod blocks a prohibited message before it is persisted.
- Onboarding answer assigns the configured role and is covered by a backend test.
- Admin actions create audit log entries.
- Moderation UI component test and Playwright E2E pass.
- Existing backend/frontend full regressions pass.

## Failure Criteria

- A blocked message appears in message list/search.
- Onboarding can assign roles not configured by its answer.
- Audit log misses rule/admin actions.
- Moderation UI renders static state without store-backed actions.

## Assumptions

- T12 uses in-memory services to stay consistent with previous slices.
- `MANAGE_MESSAGES` is sufficient to manage AutoMod rules in this skeleton.
- Guild owner/admin can configure onboarding questions.
- Audit events are append-only records in a new moderation boundary.
