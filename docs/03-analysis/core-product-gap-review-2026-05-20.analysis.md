# Core Product Gap Review

Date: 2026-05-20
Scope: T00-T49 Discord Clone Core Roadmap

## Summary

The main product roadmap is effectively complete, but not fully closed.

| Metric | Count | Rate |
| --- | ---: | ---: |
| Core roadmap tasks | 50 | 100.0% |
| Tasks with completion reports | 49 | 98.0% |
| Tasks missing completion report | 1 | 2.0% |

## Finding

| Task | Status | Evidence | Decision |
| --- | --- | --- | --- |
| T32 Dependency, SBOM & Vulnerability Gate | Open | Plan and Design exist, but no `docs/04-report/T32-*.report.md` exists and no `qa/security-gate.ps1` or SBOM/vulnerability CI gate is present. | Reopen as a core/security release-gate task and run before lower-priority polish. |

## Completed Core Coverage

Reports exist for T00-T31 and T33-T49. This means the functional Discord-clone core is largely represented by implementation reports:

- Identity, guild/channel/permission, invite, message, Gateway, shell UI.
- Friendship, DM, presence, attachments, reactions, threads, moderation/audit.
- Voice/stage/premium, operational hardening, persistence, observability.
- PWA, desktop/mobile decision, production secret/session/runtime controls.
- WebSocket, realtime reconciliation, CSP reporting, backup/restore, cross-node fanout.
- LiveKit, OpenAPI, notifications, search/moderation reports, admin console, upload safety, accessibility, bot/webhook, and server events.

## Security Review Notes

Relevant frontend security guidance was checked for Vue/JavaScript work. The remaining core gap aligns with supply-chain guidance: third-party JavaScript and dependency risk should be controlled by repeatable scanning, artifact generation, and CI release gates.

No Java-specific local security-best-practices reference is available in the current skill bundle, so backend supply-chain review should use repository-owned Gradle evidence and CI policy rather than relying on an unavailable language reference.

## Required Follow-Up

| New Priority | Task | Reason |
| ---: | --- | --- |
| 1 | T32 Dependency, SBOM & Vulnerability Gate | Only missing T00-T49 core completion report; also a security release gate. |
| 2 | T158 Kafka Gateway CI Failure Artifacts | Continue centralization diagnostics after the core security gate is closed. |

## Product Completion Interpretation

- Feature/product core: 49/50 complete = 98.0%.
- Active operational/security backlog after T157: 30 completed and 38 remaining = 44.1%.
- Practical read: user-facing clone functionality is almost complete, while production-grade operational/security hardening is around halfway through the current residual backlog.
