# T29 Tauri Desktop App Shell Report

작성일: 2026-05-15  
Slice: T29 Tauri Desktop App Shell

## Summary

T29 added the desktop app shell baseline using Tauri 2 configuration, a minimal Rust scaffold, and a TypeScript desktop capability adapter. The desktop shell consumes shared platform capability contracts and keeps `apps/web` free from direct Tauri imports.

## Completed

- Added `apps/desktop` workspace.
- Added `@discord-clone/desktop` package.
- Added Tauri 2 config with minimal permissions.
- Added Rust scaffold files: `Cargo.toml`, `build.rs`, `src/main.rs`.
- Added empty Tauri capability allowlist.
- Added desktop adapter placeholder for notification, deep link, tray, and window state.
- Added contract tests for shared capability alignment, placeholder adapter behavior, no direct Tauri imports in `apps/web`, and empty native permission baseline.

## Verification

- `npm run test -w apps/desktop`: PASS
- `npm test --workspaces`: PASS
- `npm run build -w apps/web`: PASS
- `npm run build -w apps/desktop`: FAIL because `cargo` is not installed locally

## Next

- Install Rust/Cargo and run `npm run build -w apps/desktop` to generate packaging evidence and `Cargo.lock`.
- T31 should wire desktop contract tests into CI if root workspace tests are not already used in remote CI.
