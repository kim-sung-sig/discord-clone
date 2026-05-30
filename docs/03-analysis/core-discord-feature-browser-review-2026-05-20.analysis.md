# Core Discord Feature Browser Review

Date: 2026-05-20
Reviewer: Codex
Scope: Web Discord shell, chat, DM, forum, voice, stage, and related backend evidence.

## Summary

| Area | Status | Evidence |
| --- | --- | --- |
| Guild/channel shell | Implemented as a broad web shell | `apps/web/tests/e2e/app-shell.spec.ts` passed 13/13 on clean port 3030. Screenshot: `output/playwright/discord-core-01-shell.png`. |
| Text chat | Implemented for shell state, channel scoping, composer, mentions, reactions, tombstone, and attachment metadata | Screenshot: `output/playwright/discord-core-02-chat-attachment.png`. |
| DM/group DM | Implemented as shell flow with member mutation and group call skeleton | Covered by `app-shell.spec.ts`. |
| Forum/thread | Implemented as shell flow with tags, archived thread write guard, reopen, and post creation | Covered by `app-shell.spec.ts`. |
| Voice | Partial: join/leave/control state is implemented; real media exchange is not proven | UI explicitly shows `SFU skeleton` and `LIVEKIT_SKELETON`. Screenshot: `output/playwright/discord-core-03-voice.png`. |
| Stage/soundboard/premium | Implemented as skeleton workflows | Screenshot: `output/playwright/discord-core-04-stage-premium.png`. |
| Real backend flow | Partial: frontend has a real backend smoke button, but e2e is environment-gated and skipped by default | `apps/web/tests/e2e/real-backend.spec.ts` is skipped unless `REAL_BACKEND_E2E=1`. |
| Mobile shell | Implemented at visual smoke level | Screenshot: `apps/web/test-results/visual-smoke/mobile-shell.png`. |

## Verification

- `npm run e2e -w apps/web -- --project=chromium --workers=1 tests/e2e/app-shell.spec.ts`
  - Result: 13 passed.
- `npm run e2e -w apps/web -- --project=chromium --workers=1 tests/e2e/visual-smoke.spec.ts`
  - Result: 1 passed.
- Initial full e2e run against the already-running port 3000 failed at `page.goto` with `net::ERR_ABORTED`.
  - Re-run on a clean Playwright-managed port 3030 passed.
  - This points to stale dev-server or port-state instability, not a reproduced feature regression.

## Findings

### Major

1. Voice is not a full Discord-equivalent media implementation yet.
   - Evidence: `apps/web/components/shell/VoicePanel.vue` labels the feature `SFU skeleton`; tests expect `LIVEKIT_SKELETON`.
   - Impact: users can validate voice state transitions, but not real microphone/audio/video track exchange.
   - Existing related residuals: real LiveKit media smoke and session-scoped token claims remain documented in T41 analysis.

2. Real backend browser smoke is not part of the default e2e gate.
   - Evidence: `apps/web/tests/e2e/real-backend.spec.ts` is skipped unless `REAL_BACKEND_E2E=1`.
   - Impact: default browser verification mostly proves shell behavior, not full Spring Boot-backed chat/voice/stage workflow.

3. Parallel e2e against a stale existing port can fail before page load.
   - Evidence: full e2e against `127.0.0.1:3000` failed with `page.goto: net::ERR_ABORTED`; clean port 3030 single-worker e2e passed.
   - Impact: local review can produce false negatives if it reuses a stale dev server.

### Minor

1. Desktop screenshot shows narrow panel text clipping/overlap in dense dashboard areas.
   - Evidence: role permission, gateway, and voice controls are visible but cramped in full-page screenshots.
   - Impact: core flows are usable for QA, but visual polish is below production Discord quality.

2. Some UI labels still expose skeleton terminology to end users.
   - Evidence: `SFU skeleton`, `Skeleton entitlement`, `LIVEKIT_SKELETON`.
   - Impact: acceptable for internal QA, not for product-facing release.

## Task Follow-Ups

| Task | Priority | Reason |
| --- | --- | --- |
| T164 Real Backend Browser Smoke Default Gate | P2 | Make login -> backend guild/channel/message/voice/stage proof easier to run without manual environment setup. |
| T165 Real LiveKit Media Smoke | P1 | Prove actual audio/video track exchange, not only voice state transitions and token issuance. |
| T166 Discord Shell Layout Compression Pass | P2 | Fix clipped/overlapping dense desktop panels before product demo use. |

## Practical Read

The project has a broad Discord clone surface: guild/channel, chat, mentions, attachments, reactions, DM/group DM, forum, moderation, gateway status, voice state, stage, soundboard, and premium skeletons are visible and test-covered in the web shell.

The remaining gap is depth: default UI tests mostly validate deterministic shell behavior, while full real-backend and real-media workflows are still gated or skeletal.
