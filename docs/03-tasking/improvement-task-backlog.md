# Improvement Task Backlog

작성일: 2026-05-14  
목적: `docs/05-feedback`와 runtime review에서 반복 확인된 개선사항을 실행 가능한 후속 task로 승격한다.

## Summary

| Task | Theme | Priority | Source Feedback |
| --- | --- | --- | --- |
| T16 | Persistence/PostgreSQL Migration | P0 | T01/T02/T03/T04/T07/T08/T09/T10/T11 feedback and design notes |
| T17 | Observability/Structured Logging | P1 | T15-FB-001, runtime API review |
| T18 | Realtime Media/Gateway Broadcast Integration | P1 | T13 voice skeleton, T14 soundboard/stage feedback |
| T19 | Deployment Security/Abuse Controls | P1 | T15-FB-002, T15-FB-003 |
| T20 | Premium Billing/Entitlement Persistence | P2 | T14-FB-003 |
| T21 | Audit/Security Actions Expansion | P2 | T12 analysis limitation, Discord research security actions |
| T22 | Toolchain/Build Maintenance | P2 | repeated Gradle/Nuxt/Vue warning notes |
| T23 | Frontend Real API Integration Stabilization | P0 | runtime review, common task failure criterion: UI/API mismatch |
| T24 | Real Backend QA Orchestration | P0 | T23 residual risk: real-backend Playwright requires manual backend/database/env setup |
| T25 | CI QA Harness Wiring | P0 | T22/T24 residual risks: warning artifacts and real-backend harness are not wired to CI |
| T26 | Nuxt SSR CSP Nonce Hardening | P1 | T24/T25 residual risk: Nuxt script CSP currently uses `unsafe-inline` for hydration |
| T27 | Multi-Platform Frontend Architecture & Screen Contracts | P1 | user scope expansion: desktop app and mobile app/PWA planning |
| T28 | PWA & Mobile Web Shell | P1 | T27 follow-up: installable mobile web and responsive app-shell QA |
| T29 | Tauri Desktop App Shell | P2 | T27 follow-up: desktop app surface and OS capability boundary |
| T30 | Native Mobile App Decision & Expo Shell Spike | P2 | T27 follow-up: decide PWA-only vs native mobile expansion |
| T31 | Remote CI Verification & Release Gate | P0 | T25/T26 residual risk: first remote GitHub Actions run still needs observation |
| T32 | Dependency, SBOM & Vulnerability Gate | P0 | T22/T25 follow-up: supply-chain risk should be visible in CI |
| T33 | Production Secret & Config Baseline | P0 | T18/T19/T26 residual risks: production secrets and profile config need explicit gates |
| T34 | Session & Account Security Hardening | P0 | T01/T16/T17 follow-up: refresh rotation, session revocation, suspicious login |
| T35 | Redis-backed Runtime Controls | P0 | T08/T19 residual risk: runtime controls still have local-memory paths |
| T36 | Real WebSocket Gateway Transport | P0 | T05/T18 residual risk: realtime delivery is not yet production WebSocket transport |
| T37 | Frontend Realtime State Reconciliation | P0 | T23/T36 follow-up: REST writes and Gateway events need duplicate/order handling |
| T38 | CSP Reporting & Style Policy Hardening | P1 | T26 residual risk: CSP reporting and style policy hardening remain open |
| T39 | Backup, Restore & Migration Drill | P1 | T16 follow-up: persistence needs recovery rehearsal |
| T40 | Cross-node Gateway Fanout | P1 | T18/T36 scale follow-up: multi-node event delivery consistency |
| T41 | LiveKit Production Voice Integration | P1 | T13/T18 residual risk: real secret-backed media integration remains deferred |
| T42 | OpenAPI & Frontend Client Contract | P1 | T23/T25 follow-up: API/client drift should be caught by CI |
| T43 | Notification & Mention Inbox | P1 | T04/T08/T30 product expansion: mention/unread UX across platforms |
| T44 | Message Search & Moderation Reports | P1 | T04/T12/T21 product and safety expansion |
| T45 | Admin Console & Role Permission UX | P1 | T02B/T02C/T21 follow-up: permission management needs operator-grade UX |
| T46 | Upload Security & Content Safety | P1 | T09/T19 follow-up: attachment validation and unsafe preview boundaries |
| T47 | Accessibility & Responsive UX Pass | P2 | T06/T28 follow-up: keyboard/focus/mobile quality hardening |
| T48 | Bot & Webhook Skeleton | P2 | Discord parity expansion after Gateway/audit/security foundation |
| T49 | Server Events & Scheduling | P2 | Discord parity expansion for community event workflows |

## Source Clusters

### Persistence Cluster

Sources:

- T01/T02 design: persistence intentionally deferred.
- T02C report: later persistence-backed repository boundaries needed.
- T07 feedback: DM message persistence is a skeleton.
- T08 analysis: read markers need persisted message sequence.
- T09 analysis: message attachment persistence is skeleton.
- T10 feedback: reaction uniqueness constraints deferred.
- T11 feedback: thread/forum storage and thread-message integration deferred.

Promoted task: T16.

### Realtime/Media Cluster

Sources:

- T13 feedback: no real WebRTC/LiveKit media connection yet; voice event persistence deferred.
- T14 feedback: soundboard play event is a projection only; stage channel is modeled as voice session.

Promoted task: T18.

### Operational/Security Cluster

Sources:

- T15 feedback: request id is not connected to MDC/OpenTelemetry.
- T15 feedback: API CSP does not cover Nuxt HTML.
- T15 feedback: no rate limiting or abuse protection.

Promoted tasks: T17 and T19.

### Premium/Commercial Cluster

Sources:

- T14 feedback: premium grant is a test skeleton.
- T14 analysis: billing, subscription lifecycle, tax/refund/fraud are out of scope.

Promoted task: T20.

### Audit/Safety Cluster

Sources:

- T12 analysis: audit coverage is limited to onboarding/AutoMod actions.
- Discord research: Activity Alerts and Security Actions are product-level moderation capabilities.

Promoted task: T21.

### Toolchain Cluster

Sources:

- Repeated T00-T15 reports: Gradle 9 deprecation, Nuxt sourcemap warning, Vue package export deprecation.

Promoted task: T22.

### Frontend/API Consistency Cluster

Sources:

- Common task failure criterion: API allows but UI hides, or UI succeeds while backend rejects.
- Runtime review: API smoke now exists, but Playwright still validates mostly local shell state.

Promoted task: T23.

### Runtime QA Orchestration Cluster

Sources:

- T23 stabilization residual risk: real-backend Playwright was not run in that pass because service/database setup is environment-gated.
- Runtime review: API smoke is reusable, but full backend + frontend orchestration still requires manual env setup.

Promoted task: T24.

### CI Quality Gate Cluster

Sources:

- T22 residual risk: warning regression is visible locally, but CI artifact upload is not wired.
- T24 residual risk: CI workflow integration remains future work.

Promoted task: T25.

### CSP Hardening Cluster

Sources:

- T24 residual risk: Nuxt CSP permits inline scripts for hydration compatibility.
- T25 residual risk: Nuxt inline-script CSP remains production-hardening follow-up.

Promoted task: T26.

### Multi-Platform Frontend Cluster

Sources:

- User scope expansion: frontend screen plan must cover desktop app and mobile app via PWA or native.
- Existing T06 responsive shell is web-first and does not define installable PWA, desktop shell, or native mobile screen contracts.
- Existing architecture has `apps/web` only, so shared UI/API/platform contracts need to be planned before implementation.

Promoted tasks: T27, T28, T29, T30.

### Release/Supply-chain Security Cluster

Sources:

- T25 report: first remote GitHub Actions execution can reveal runner-specific issues not reproduced locally.
- T22 warning-budget work made toolchain drift visible, but dependency vulnerability/SBOM gates are not part of the current CI baseline.
- T18/T26 residual risks depend on production secret handling and deployment profile validation.

Promoted tasks: T31, T32, T33.

### Account and Runtime Control Cluster

Sources:

- T01 implemented identity/session basics, while stronger account security such as refresh token reuse detection and device session revocation remains a hardening layer.
- T08 presence and T19 rate limiting introduced runtime controls, but production Redis-backed paths should become explicit before scale testing.
- T17 observability makes suspicious-login and security-action evidence useful once the events exist.

Promoted tasks: T34, T35.

### Production Realtime Cluster

Sources:

- T05/T18 established Gateway semantics and media/stage/soundboard event broadcast, but real WebSocket transport and cross-node fanout remain deferred.
- T23 real API integration and future T28~T30 surfaces need frontend reconciliation so REST writes and Gateway echo events do not duplicate state.

Promoted tasks: T36, T37, T40, T41.

### Browser Security and Recovery Cluster

Sources:

- T26 removed script `unsafe-inline`, but CSP reporting and style policy hardening remain separate work.
- T16 persistence gives the project real durable state, which requires backup/restore and migration rehearsal.

Promoted tasks: T38, T39.

### API Contract and Product Expansion Cluster

Sources:

- T23 showed the cost of frontend/backend drift; OpenAPI/client contract checks should become an automated gate.
- Discord parity still needs notification inbox, search/report workflows, admin permission UX, bot/webhook, server events, upload safety, and accessibility passes.
- T27~T30 platform expansion increases the value of shared API contracts and accessibility/responsive quality gates.

Promoted tasks: T42, T43, T44, T45, T46, T47, T48, T49.

## Recommended Execution Order

1. T16 Persistence/PostgreSQL Migration
2. T23 Frontend Real API Integration Stabilization
3. T17 Observability/Structured Logging
4. T19 Deployment Security/Abuse Controls
5. T18 Realtime Media/Gateway Broadcast Integration
6. T21 Audit/Security Actions Expansion
7. T20 Premium Billing/Entitlement Persistence
8. T22 Toolchain/Build Maintenance
9. T24 Real Backend QA Orchestration
10. T25 CI QA Harness Wiring
11. T26 Nuxt SSR CSP Nonce Hardening
12. T27 Multi-Platform Frontend Architecture & Screen Contracts
13. T28 PWA & Mobile Web Shell
14. T29 Tauri Desktop App Shell
15. T30 Native Mobile App Decision & Expo Shell Spike
16. T31 Remote CI Verification & Release Gate
17. T32 Dependency, SBOM & Vulnerability Gate
18. T33 Production Secret & Config Baseline
19. T34 Session & Account Security Hardening
20. T35 Redis-backed Runtime Controls
21. T36 Real WebSocket Gateway Transport
22. T37 Frontend Realtime State Reconciliation
23. T38 CSP Reporting & Style Policy Hardening
24. T39 Backup, Restore & Migration Drill
25. T40 Cross-node Gateway Fanout
26. T41 LiveKit Production Voice Integration
27. T42 OpenAPI & Frontend Client Contract
28. T43 Notification & Mention Inbox
29. T44 Message Search & Moderation Reports
30. T45 Admin Console & Role Permission UX
31. T46 Upload Security & Content Safety
32. T47 Accessibility & Responsive UX Pass
33. T48 Bot & Webhook Skeleton
34. T49 Server Events & Scheduling

Reasoning:

- Persistence must come before real frontend/backend state integration and most production controls.
- Frontend real API integration should follow persistence to prevent local-store-only UX drift.
- Observability and security controls become more valuable once real runtime flows exist.
- Media and premium are larger capability expansions and should use the hardened/persistent foundation.
- Real-backend QA orchestration closes the remaining repeatability gap after frontend/backend integration exists.
- CI wiring promotes the now-repeatable local QA harnesses into automated quality gates.
- CSP nonce hardening should follow CI wiring so runtime hydration regressions remain visible while tightening browser policy.
- Multi-platform frontend work should start after CSP/runtime QA stabilization so new PWA/desktop/mobile surfaces inherit a verified web baseline instead of multiplying existing runtime gaps.
- T31~T33 should follow the multi-platform plan before new high-risk implementation expands release and supply-chain exposure.
- T34~T37 close account security, Redis-backed runtime controls, and real WebSocket consistency before cross-node/media expansion.
- T38~T42 improve browser telemetry, recovery, distributed realtime, media, and API contract reliability.
- T43~T49 are product-parity and quality expansions that should build on the stabilized security/realtime foundation.
