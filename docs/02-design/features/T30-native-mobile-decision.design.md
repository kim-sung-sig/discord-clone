# T30 Native Mobile Decision Design

Created: 2026-05-15
PDCA Phase: Design
Slice: Native Mobile Decision And Expo Spike

## 1. Decision

Decision: `PWA-first/native-later`.

The next release should continue with the Nuxt mobile PWA as the mobile product baseline and defer Expo/native implementation until product requirements prove that at least two native-only capabilities are required for the next release.

Current next-release assessment:

- Required native-only capabilities: 0.
- Native-only threshold to start Expo in parallel: 2 or more.
- Result: do not create `apps/mobile`, do not edit `package.json`, and do not implement Expo code in this task.

## 2. Options Compared

| Criteria | PWA-only | PWA-first/native-later | Native-parallel |
| --- | --- | --- | --- |
| Push | Possible only where web push is supported; weaker iOS/runtime control than native. | Use web notification/PWA capability first, then add native push when delivery reliability becomes release-critical. | Best path for APNs/FCM reliability, token lifecycle control, and mobile notification UX. |
| Background session | Limited by browser lifecycle and OS throttling. | Accept limits for next release; promote native if persistent voice, presence, or reconnect behavior becomes required. | Strongest option for background reconnect, presence, and long-running media/session workflows. |
| Media | Web media APIs can support baseline voice/media UX, but device controls and background behavior are constrained. | Validate baseline media in PWA; add native only if OS-level audio routing, call UI, or background media becomes mandatory. | Better access to native audio/video controls, permissions, and background behavior, with higher integration cost. |
| App store distribution | Cannot fully satisfy native store presence without wrappers or native app work. | Defer store distribution until acquisition/compliance needs justify native investment. | Supports App Store and Play Store distribution from the start, including review/signing overhead. |
| Native share | Web Share API may cover simple share flows on supported devices. | Use web share where available; add native share sheet only if sharing becomes a core next-release workflow. | Full native share sheet integration and file/content handoff control. |
| File picker | Browser file picker covers basic upload/download flows with platform differences. | Keep browser picker for baseline attachment flows; add native picker if advanced provider integration or media-library access is required. | Stronger native document/media picker access, but requires platform permission and test coverage. |
| QA cost | Lowest platform count, but risks under-testing mobile OS edge cases. | Moderate and controlled: mobile viewport/PWA QA now, native QA later only when justified. | Highest: separate Expo runtime, device/simulator matrix, native permissions, app store build/signing, and regression coverage. |
| Schedule risk | Lowest immediate implementation risk, but may delay native-only requirements if discovered late. | Best schedule balance: ships mobile web baseline while preserving a clear escalation rule. | Highest near-term risk because native shell, navigation, permissions, and CI/device QA start before requirements justify them. |
| Reuse of shared contracts | Reuses `api-client`, `ui-contracts`, `design-tokens`, and `platform-shell` for web/PWA only. | Reuses shared contracts now and keeps Expo constrained to the same contract names when promoted. | Can reuse shared contracts, but parallel implementation increases risk of contract drift before the PWA baseline is stable. |

## 3. Native-Only Capability Gate

Native-parallel becomes the selected track only when two or more of these are required for the next release:

- Reliable APNs/FCM push behavior beyond web push support.
- Background session continuity for voice, reconnect, presence, or uploads.
- Native media behavior such as OS call/audio integration or background media.
- App Store or Play Store distribution as a release requirement.
- Native share sheet behavior beyond Web Share API support.
- Native file/media picker behavior beyond browser file input support.

At this decision point, none of those capabilities are documented as mandatory for the next release. They remain escalation triggers, not implementation scope.

## 4. Rationale

`PWA-first/native-later` matches the T27 architecture decision: keep `apps/web` as the verified product baseline, use mobile PWA to validate single-pane information architecture, and hide platform-specific capabilities behind shared contracts. This avoids starting a second frontend runtime before product requirements justify the additional QA and release burden.

This also preserves the agent ownership boundaries in the operating model:

- The Expo Candidate Agent owns this decision record.
- `apps/mobile` is only owned after decision approval.
- Shared API, permission, unread, presence, and screen behavior must remain contract-driven rather than forked per platform.

## 5. Consequences

- Continue T28 PWA/mobile shell work as the next mobile delivery path.
- Do not scaffold Expo or create `apps/mobile` for this decision-only task.
- Do not add `apps/mobile` to npm workspaces.
- Review native escalation after PWA mobile IA, notification, media, share, and attachment flows have concrete QA results.

## 6. Residual Risks

- Web push and background behavior may be insufficient on target mobile platforms, especially if notification reliability becomes a core release requirement.
- Browser media and file picker behavior may not match native user expectations for voice, uploads, or device library access.
- Delaying Expo reduces current QA cost but can compress schedule later if two or more native-only requirements become mandatory near release.
- Shared contracts must stay stable; otherwise a later native track may inherit contract churn from web/PWA implementation.
