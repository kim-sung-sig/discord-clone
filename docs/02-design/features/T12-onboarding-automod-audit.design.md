# T12 Onboarding/AutoMod/Audit Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T12 Onboarding/AutoMod/Audit

## Backend Architecture

Create `backend/modules/moderation` as the T12 domain boundary. It owns onboarding questions, AutoMod rules, AutoMod decisions, and audit log records. Keeping these in one module is intentional for the skeleton: AutoMod decisions and onboarding/admin actions both emit audit records, so a shared in-memory service avoids premature event bus work.

### Domain Files

- `backend/modules/moderation/src/main/java/com/example/discord/moderation/OnboardingQuestion.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/OnboardingAnswer.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/AutoModRule.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/AutoModRuleType.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/AutoModDecision.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/AuditLogEntry.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/AuditLogAction.java`
- `backend/modules/moderation/src/main/java/com/example/discord/moderation/InMemoryModerationService.java`

### Boot Adapter Files

- `backend/boot/src/main/java/com/example/discord/moderation/ModerationConfiguration.java`
- `backend/boot/src/main/java/com/example/discord/moderation/ModerationController.java`
- `backend/boot/src/test/java/com/example/discord/moderation/ModerationControllerTest.java`
- Modify `backend/boot/src/main/java/com/example/discord/message/MessageController.java` to call AutoMod before `messageService.create`.

## API Contract

- `POST /api/guilds/{guildId}/onboarding/questions`
  - Requires owner/manage role capability.
  - Creates a question with answers and role grants.
- `POST /api/guilds/{guildId}/onboarding/answers`
  - Member submits answer IDs.
  - Service assigns only configured role grants through `InMemoryGuildService.assignRoleToMember`.
- `POST /api/guilds/{guildId}/automod/rules`
  - Requires `MANAGE_MESSAGES`.
  - Creates keyword/spam skeleton rule.
- `GET /api/guilds/{guildId}/audit-logs`
  - Requires `MANAGE_MESSAGES`.
  - Lists newest audit entries.

## AutoMod Flow

1. `MessageController.create` authenticates the requester and validates `SEND_MESSAGES`.
2. It calls `moderationService.evaluateMessage(guildId, channelId, requesterId, content)`.
3. If blocked, the moderation service appends an `AUTOMOD_MESSAGE_BLOCKED` audit log and the controller returns `403`.
4. If allowed, message persistence proceeds normally.

This explicitly satisfies “AutoMod blocks before persist.” The regression test must assert the blocked content is absent from `GET /messages`.

## Frontend Architecture

Add a moderation panel to the Nuxt app shell:

- `apps/web/components/shell/ModerationPanel.vue`
- Extend `apps/web/stores/shell.ts` with:
  - onboarding question/answer state
  - AutoMod rule state
  - audit log state
  - actions `submitOnboardingAnswer`, `simulateAutoModBlock`, `createAuditEntry`

The panel must show selected onboarding answer, assigned role, active AutoMod keywords, blocked message status, and audit log entries. Buttons use hydration guards to avoid SSR click loss.

## Test Strategy

- Backend domain TDD:
  - onboarding answer assigns only configured roles
  - keyword rule blocks matching content
  - audit entries append for onboarding/admin/AutoMod events
- Backend REST TDD:
  - blocked message returns 403 and is not listed
  - onboarding answer assigns role
  - admin AutoMod rule creation appends audit log
- Frontend TDD:
  - component test for moderation panel visible state/actions
  - Playwright E2E for onboarding answer, AutoMod block simulation, and audit log visibility

## Risks

- `MessageController` coupling to moderation service is acceptable for this in-memory modular monolith slice, but should later move to command pipeline/domain events.
- Spam detection is skeleton-level and should initially be deterministic keyword/repeated-token logic.
