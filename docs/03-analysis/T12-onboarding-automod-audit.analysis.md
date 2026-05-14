# T12 Onboarding/AutoMod/Audit Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T12 Onboarding/AutoMod/Audit

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| onboarding question | Met | `OnboardingQuestion`/`OnboardingAnswer`, REST create question, and Nuxt moderation panel render the question |
| role assignment from onboarding answer | Met | `ModerationControllerTest.onboardingAnswerAssignsConfiguredRole` verifies configured role grant assignment |
| AutoMod keyword rule | Met | `InMemoryModerationServiceTest.keywordAutoModRuleBlocksMatchingMessage` and MockMvc blocked-message test |
| AutoMod blocks before persist | Met | `MessageController` evaluates moderation before `messageService.create`; MockMvc verifies blocked content absent from message list |
| audit log for admin/action events | Met | AutoMod rule creation, onboarding answer, and AutoMod block append audit records |
| moderation UI test | Met | Vitest and Playwright cover onboarding, AutoMod decision, and audit log visibility |

## Gap Log

- Resolved: AutoMod had to run before message persistence, not as a post-create cleanup. Injected `InMemoryModerationService` into `MessageController.create` before `messageService.create`.
- Resolved: onboarding answer submission could have allowed arbitrary role assignment. Domain returns grants only from configured answer IDs, and REST assigns only those role IDs.
- Resolved: UI needed store-backed actions, not static moderation markup. Added Pinia moderation state/actions and `ModerationPanel`.
- Observed: worker saw one transient storage test failure; isolated rerun and full rerun passed, and no storage files changed.

## Residual Risks

- AutoMod spam detection is enum/skeleton only; implemented deterministic keyword blocking for this slice.
- Audit logs are in-memory and not durable.
- Audit coverage is limited to onboarding/AutoMod actions; all-write audit coverage remains future work.
- Moderation panel is an operations skeleton, not a full policy management UX.
