# T166 Discord Shell Layout Compression Pass Plan

Date: 2026-05-21
Status: Completed

## Problem

The desktop Discord shell screenshot review showed dense operational panels overflowing the 1366px viewport. The right-side forum/admin/moderation/voice surface clipped buttons and compact status labels, and the skip link occupied visible layout space before focus.

## Scope

- Add a browser-level layout guard for the desktop shell.
- Compress the fixed desktop grid widths without changing the information architecture.
- Keep gateway, role permission, moderation, voice, invite, and experience panels inside the viewport.
- Fix text wrapping for role permission diff, preview-as-role, privileged audit, and moderation audit entries.
- Regenerate visual smoke screenshots for review evidence.

## Out Of Scope

- Redesigning the shell navigation model.
- Mobile layout redesign.
- Real backend integration changes.
- New Discord product features.

## Acceptance Criteria

- A Playwright test fails before the CSS fix when operational panels overflow.
- The same test passes after implementation.
- Existing visual smoke still passes on an isolated local Nuxt port.
- Updated screenshot shows no visible panel overlap or right-side clipping in the desktop shell.

## Wiki Used

- `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Frontend Client Architecture.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\QA Infra Operations.md`
- `docs/03-tasking/post-t106-residual-task-priority.md`
