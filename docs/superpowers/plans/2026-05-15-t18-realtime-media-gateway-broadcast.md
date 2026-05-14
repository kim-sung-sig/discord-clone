# T18 Realtime Media/Gateway Broadcast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect voice, stage, and soundboard REST mutations to channel-authorized gateway delivery while isolating LiveKit-compatible token signing behind a provider interface.

**Architecture:** Spring Boot remains the media control plane. Voice token issuance moves behind a signer port, and controllers publish sanitized channel-scoped events through the existing gateway service so current gateway poll filtering enforces visibility.

**Tech Stack:** Spring Boot 3.3, Java records/interfaces, MockMvc, AssertJ, existing in-memory gateway/voice/experience modules.

---

### Task 1: Planning

**Files:**
- Create: `docs/01-plan/features/T18-realtime-media-gateway-broadcast.plan.md`
- Create: `docs/02-design/features/T18-realtime-media-gateway-broadcast.design.md`
- Create: `docs/superpowers/plans/2026-05-15-t18-realtime-media-gateway-broadcast.md`
- Modify: `.bkit-memory.json`

- [ ] Commit planning docs with `docs: plan T18 realtime gateway broadcast`.

### Task 2: Voice Token Boundary and Gateway Fanout

**Files:**
- Create: `backend/modules/voice/src/main/java/com/example/discord/voice/VoiceTokenSigner.java`
- Create: `backend/modules/voice/src/main/java/com/example/discord/voice/VoiceTokenSigningRequest.java`
- Create: `backend/modules/voice/src/main/java/com/example/discord/voice/SkeletonLiveKitTokenSigner.java`
- Modify: `backend/modules/voice/src/main/java/com/example/discord/voice/InMemoryVoiceService.java`
- Modify: `backend/boot/src/main/java/com/example/discord/voice/VoiceConfiguration.java`
- Modify: `backend/boot/src/main/java/com/example/discord/voice/VoiceController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/voice/VoiceControllerTest.java`

- [ ] Write failing MockMvc tests for voice join/update/leave gateway events and hidden-channel non-delivery.
- [ ] Write failing unit test for `SkeletonLiveKitTokenSigner` provider, room, participant, ttl, and non-production token prefix.
- [ ] Run targeted voice tests and verify RED.
- [ ] Implement signer port and wire gateway publish after successful voice mutations.
- [ ] Run targeted voice tests and verify GREEN.
- [ ] Commit with `feat: broadcast voice gateway events`.

### Task 3: Stage and Soundboard Gateway Fanout

**Files:**
- Modify: `backend/boot/src/main/java/com/example/discord/experience/StageController.java`
- Modify: `backend/boot/src/main/java/com/example/discord/experience/SoundboardController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/experience/ExperienceControllerTest.java`

- [ ] Write failing MockMvc tests for stage update fanout and soundboard play fanout.
- [ ] Run targeted experience tests and verify RED.
- [ ] Publish sanitized channel-scoped events after successful stage and soundboard mutations.
- [ ] Run targeted experience tests and verify GREEN.
- [ ] Commit with `feat: broadcast experience gateway events`.

### Task 4: Check/Report

**Files:**
- Create: `docs/03-analysis/T18-realtime-media-gateway-broadcast.analysis.md`
- Create: `docs/04-report/T18-realtime-media-gateway-broadcast.report.md`
- Modify: `.bkit-memory.json`

- [ ] Run targeted voice tests.
- [ ] Run targeted experience tests.
- [ ] Run full backend test suite.
- [ ] Record success criteria, failure criteria, test evidence, and residual risks.
- [ ] Commit with `docs: record T18 realtime gateway PDCA`.
