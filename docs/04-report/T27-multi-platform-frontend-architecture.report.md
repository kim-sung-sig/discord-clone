# T27 Multi-Platform Frontend Architecture Report

작성일: 2026-05-15  
Slice: T27 Multi-Platform Frontend Architecture & Screen Contracts

## Summary

T27 added shared frontend contracts for web desktop, mobile PWA, desktop app, and native mobile candidate surfaces. The contracts define screen layout, navigation guard requirements, permission visibility, presence/unread/error shapes, and platform capabilities without exposing native adapter implementations to UI consumers.

## Completed

- Added `packages/*` to npm workspaces.
- Added `@discord-clone/ui-contracts`.
- Added `@discord-clone/platform-shell`.
- Added screen contracts for `web-desktop`, `pwa-mobile`, `desktop-app`, and `native-mobile`.
- Added auth/permission navigation guard fields for deep link and restored-channel entry.
- Added permission contract coverage via exhaustive `Record`.
- Added platform capability tests that keep native-only capabilities out of PWA MVP.
- Updated root `e2e` to target the browser E2E workspace explicitly.
- Updated implementation plan to include `package-lock.json` for workspace changes.

## Verification

- `npm test --workspaces`: PASS
- `$env:NUXT_DEV_PORT='3012'; npm run e2e`: PASS, 15 passed and 1 real-backend test skipped
- `npm run build -w apps/web`: PASS
- `git diff --check`: PASS

## Review Outcome

Spec and quality review found no remaining P0/P1 issue after fixes. P2 source-only package exports remain documented as a future packaging consideration.

## Next

- T28: implement PWA/mobile shell using the new contracts.
- T29: implement Tauri desktop shell after T28 web baseline remains green.
