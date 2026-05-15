# T21 Audit/Security Actions Expansion Report

작성일: 2026-05-15  
PDCA Phase: Report  
Slice: T21 Audit/Security Actions Expansion

## Summary

T21 expanded audit coverage beyond AutoMod/premium and added a security alert skeleton. Admins can now filter audit logs by action, actor, and target, while AutoMod blocks generate security alerts without persisting unsafe messages.

## Delivered

- Added audit actions for role assignment, invite deletion, message moderation, and stage moderation.
- Added audit log filters: `action`, `actorId`, `targetId`.
- Added audit hooks in guild, invite, message, and stage controllers.
- Added `SecurityAlert` domain record and `/api/guilds/{guildId}/security-alerts` endpoint.
- AutoMod block now creates an `AUTOMOD_BLOCK` alert at policy-decision time.
- Added MockMvc coverage for searchable audit actions and security alert/no-message-persistence behavior.

## Test Evidence

- `./gradlew.bat :backend:boot:test --tests com.example.discord.moderation.ModerationControllerTest --rerun-tasks`: PASS
- `./gradlew.bat test`: PASS

## Commits

- `3ea8aa9 docs: plan T21 audit security actions`
- `eefab00 feat: expand audit search and security alerts`

## Residual Risks

- Audit and security alert records are not durable yet.
- Coverage is representative and requirement-driven, not a full exhaustive audit matrix.
- Security alerts have a minimal severity/type taxonomy.

## Next Recommended Task

Proceed to T22 Toolchain/Build Maintenance.
