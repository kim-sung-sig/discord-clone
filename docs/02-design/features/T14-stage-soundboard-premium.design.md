# T14 Stage/Soundboard/Premium Skeleton Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T14 Stage/Soundboard/Premium Skeleton

## Backend Architecture

Create `backend/modules/experience` as the product-experience boundary above voice/guild/permission. The module owns deterministic in-memory domain state for Stage, Soundboard, Premium, Catalog, and Quest skeletons. Boot REST adapters perform authorization with `InMemoryGuildService` and pass only authorized commands into the domain service.

### Domain Files

- `StageSession`: channel, topic, moderator ids, speaker ids, audience ids, pending speak requests.
- `StageParticipantRole`: `MODERATOR`, `SPEAKER`, `AUDIENCE`.
- `StageSpeakRequest`: pending request from an audience member.
- `SoundboardSound`: guild sound metadata.
- `SoundboardPlayEvent`: channel/user/sound playback projection.
- `PremiumFeature`: stable feature key constants.
- `Entitlement`: user/guild feature grant with expiry skeleton.
- `CatalogItem`: shop/catalog response item.
- `Quest`: quest skeleton response item.
- `InMemoryExperienceService`: stage transitions, soundboard state, entitlement checks, catalog/quest list.

### Boot Adapter Files

- `ExperienceConfiguration`
- `StageController`
- `SoundboardController`
- `PremiumController`
- `ExperienceControllerTest`
- Modify `settings.gradle.kts` and `backend/boot/build.gradle.kts`.

## API Contract

- `POST /api/stage/channels/{channelId}/sessions`
  - Auth required.
  - Requires stage moderator permission.
  - Creates or replaces active stage session with topic.
- `POST /api/stage/sessions/{sessionId}/request-to-speak`
  - Auth required.
  - Requires channel visibility.
  - Adds requester to audience and pending requests.
- `PUT /api/stage/sessions/{sessionId}/speakers/{userId}`
  - Auth required.
  - Requires moderator permission.
  - Approves pending user as speaker.
- `PUT /api/stage/sessions/{sessionId}/audience/{userId}`
  - Auth required.
  - Requires moderator permission.
  - Moves user back to audience.
- `GET /api/stage/channels/{channelId}/sessions/active`
  - Returns active session projection.
- `POST /api/soundboard/guilds/{guildId}/sounds`
  - Requires `MANAGE_EXPRESSIONS`.
  - Registers sound metadata only.
- `POST /api/soundboard/channels/{channelId}/sounds/{soundId}/play`
  - Requires channel visibility and existing sound.
  - Returns playback event skeleton.
- `GET /api/soundboard/guilds/{guildId}/sounds`
  - Lists registered sounds.
- `GET /api/premium/catalog`
  - Returns shop/catalog skeleton.
- `GET /api/premium/quests`
  - Returns quest skeletons.
- `POST /api/premium/users/{userId}/entitlements`
  - Grants test entitlement.
- `GET /api/premium/users/{userId}/features/{featureKey}`
  - Checks entitlement gate.

## Permission Model

`InMemoryGuildService` remains the authority for guild membership/channel visibility. Stage moderation uses guild owner or `MANAGE_CHANNELS`. Soundboard sound creation uses `MANAGE_EXPRESSIONS`; sound playback uses channel visibility and a valid sound id. Premium gates do not trust the client; feature access is derived only from server-side entitlement records.

## Frontend Architecture

Add `ExperiencePanel.vue` and extend `apps/web/stores/shell.ts` with:

- active stage session, pending request state, and role projection.
- soundboard sound list and most recent play event.
- premium catalog, quests, entitlement gate result.
- actions `startStageSession`, `requestToSpeak`, `approveStageSpeaker`, `moveStageAudience`, `createSoundboardSound`, `playSoundboardSound`, `grantPremiumEntitlement`, `checkPremiumFeature`.

The UI should keep Stage/Soundboard/Premium visually distinct but in one panel to keep the T14 smoke path short and deterministic.

## Test Strategy

- Domain TDD:
  - audience request remains pending and cannot speak before approval.
  - moderator approval moves pending user to speaker.
  - move-to-audience removes speaker role.
  - entitlement gate is false before grant and true after grant.
- REST TDD:
  - non-moderator cannot start/approve stage.
  - sound creation requires `MANAGE_EXPRESSIONS`.
  - sound playback validates channel visibility.
  - premium feature check is server-side.
- Frontend TDD:
  - component test for stage/soundboard/premium state transitions.
  - Playwright smoke for request, approval, audience move, sound playback, entitlement gate.

## Risks

- Stage channels are modeled as voice-channel sessions in T14. A future schema migration should add an explicit `GUILD_STAGE_VOICE` type if the channel module evolves.
- In-memory entitlements are not a billing implementation. API names must stay clearly skeleton/test oriented until payment provider integration exists.
- Soundboard play events are projections, not actual audio. UI wording must not imply real audio playback.
