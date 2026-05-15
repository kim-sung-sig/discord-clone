# Multi-Platform Frontend Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Discord clone frontend roadmap from Nuxt web-only to PWA mobile, Tauri desktop, and an Expo native-mobile decision path without forking product behavior.

**Architecture:** Keep `apps/web` as the verified product baseline. Add shared frontend contracts under `packages/*`, then add platform shells that consume those contracts instead of duplicating auth, permission, unread, presence, and API behavior.

**Tech Stack:** Nuxt 4, Vue 3, Pinia, Vitest, Playwright, PWA manifest/service worker, Tauri 2, Expo React Native candidate, npm workspaces.

---

## File Structure

- Modify: `package.json` to include future `packages/*`, `apps/desktop`, and `apps/mobile` workspaces when those tasks start.
- Create: `packages/ui-contracts/package.json` for screen, navigation, permission, presence, unread, and error contracts.
- Create: `packages/ui-contracts/src/screens.ts` for platform-neutral route and pane definitions.
- Create: `packages/platform-shell/package.json` for platform capability interfaces.
- Create: `packages/platform-shell/src/capabilities.ts` for notification, deep link, tray, offline, push, and native picker boundaries.
- Modify: `apps/web/nuxt.config.ts` for PWA metadata when T28 starts.
- Create: `apps/web/public/manifest.webmanifest` for installability.
- Create: `apps/web/server/plugins/pwa-offline.ts` or equivalent Nitro/service worker integration for offline shell behavior.
- Create: `apps/web/tests/e2e/pwa-mobile.spec.ts` for mobile viewport shell QA.
- Create: `apps/web/tests/components/platform-contracts.test.ts` for contract usage in web components.
- Create: `apps/desktop/package.json` for Tauri workspace.
- Create: `apps/desktop/src-tauri/tauri.conf.json` for minimal desktop shell configuration.
- Create: `apps/desktop/src/capabilities.ts` for desktop adapter implementations.
- Create: `apps/desktop/tests/desktop-shell.contract.test.ts` for desktop boundary checks.
- Create: `apps/mobile/package.json` only if T30 decides to keep the Expo shell.
- Create: `apps/mobile/app/(auth)/login.tsx` and `apps/mobile/app/(main)/channels.tsx` for the native candidate spike.
- Create: `apps/mobile/tests/navigation.contract.test.tsx` for native navigation contract checks.

## Task 1: Shared UI And Platform Contracts

**Files:**
- Modify: `package.json`
- Create: `packages/ui-contracts/package.json`
- Create: `packages/ui-contracts/src/screens.ts`
- Create: `packages/ui-contracts/src/permissions.ts`
- Create: `packages/platform-shell/package.json`
- Create: `packages/platform-shell/src/capabilities.ts`
- Test: `packages/ui-contracts/src/screens.test.ts`
- Test: `packages/platform-shell/src/capabilities.test.ts`

- [ ] **Step 1: Add npm workspaces for shared packages**

Change root `package.json` workspaces to:

```json
{
  "workspaces": [
    "apps/web",
    "packages/*"
  ]
}
```

- [ ] **Step 2: Create contract packages**

Create `packages/ui-contracts/package.json`:

```json
{
  "name": "@discord-clone/ui-contracts",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest"
  },
  "devDependencies": {
    "vitest": "4.1.6",
    "typescript": "6.0.3"
  }
}
```

Create `packages/platform-shell/package.json`:

```json
{
  "name": "@discord-clone/platform-shell",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest"
  },
  "dependencies": {
    "@discord-clone/ui-contracts": "0.1.0"
  },
  "devDependencies": {
    "vitest": "4.1.6",
    "typescript": "6.0.3"
  }
}
```

- [ ] **Step 3: Define screen contracts**

Create `packages/ui-contracts/src/screens.ts`:

```ts
export type PlatformSurface = 'web-desktop' | 'pwa-mobile' | 'desktop-app' | 'native-mobile'

export type ShellPane = 'server-rail' | 'channel-list' | 'chat' | 'member-list' | 'voice' | 'me'

export interface ScreenContract {
  readonly surface: PlatformSurface
  readonly primaryPane: ShellPane
  readonly visiblePanes: readonly ShellPane[]
  readonly navigation: 'multi-pane' | 'single-pane-tabs' | 'single-pane-stack'
}

export const screenContracts: readonly ScreenContract[] = [
  {
    surface: 'web-desktop',
    primaryPane: 'chat',
    visiblePanes: ['server-rail', 'channel-list', 'chat', 'member-list', 'voice', 'me'],
    navigation: 'multi-pane'
  },
  {
    surface: 'pwa-mobile',
    primaryPane: 'chat',
    visiblePanes: ['chat', 'voice', 'me'],
    navigation: 'single-pane-tabs'
  },
  {
    surface: 'desktop-app',
    primaryPane: 'chat',
    visiblePanes: ['server-rail', 'channel-list', 'chat', 'member-list', 'voice', 'me'],
    navigation: 'multi-pane'
  },
  {
    surface: 'native-mobile',
    primaryPane: 'chat',
    visiblePanes: ['chat', 'voice', 'me'],
    navigation: 'single-pane-stack'
  }
]
```

- [ ] **Step 4: Define platform capabilities**

Create `packages/platform-shell/src/capabilities.ts`:

```ts
import type { PlatformSurface } from '@discord-clone/ui-contracts/src/screens'

export type CapabilityName =
  | 'notification'
  | 'deep-link'
  | 'tray'
  | 'offline-shell'
  | 'push'
  | 'background-session'
  | 'native-file-picker'

export interface PlatformCapability {
  readonly surface: PlatformSurface
  readonly name: CapabilityName
  readonly requiredForMvp: boolean
}

export const platformCapabilities: readonly PlatformCapability[] = [
  { surface: 'pwa-mobile', name: 'offline-shell', requiredForMvp: true },
  { surface: 'desktop-app', name: 'notification', requiredForMvp: true },
  { surface: 'desktop-app', name: 'deep-link', requiredForMvp: false },
  { surface: 'desktop-app', name: 'tray', requiredForMvp: false },
  { surface: 'native-mobile', name: 'push', requiredForMvp: false },
  { surface: 'native-mobile', name: 'background-session', requiredForMvp: false },
  { surface: 'native-mobile', name: 'native-file-picker', requiredForMvp: false }
]
```

- [ ] **Step 5: Add contract tests and run them**

Create tests that assert every surface has one contract and no native-only capability is required for PWA MVP.

Run:

```powershell
npm test --workspaces
```

Expected: all workspace tests pass.

- [ ] **Step 6: Commit**

```powershell
git add package.json packages/ui-contracts packages/platform-shell
git commit -m "feat: add shared frontend platform contracts"
```

## Task 2: PWA And Mobile Web Shell

**Files:**
- Modify: `apps/web/nuxt.config.ts`
- Create: `apps/web/public/manifest.webmanifest`
- Modify: `apps/web/app.vue` or shell layout components only where current structure requires it
- Create: `apps/web/tests/e2e/pwa-mobile.spec.ts`
- Create: `apps/web/tests/components/platform-contracts.test.ts`

- [ ] **Step 1: Write mobile viewport Playwright test**

Create `apps/web/tests/e2e/pwa-mobile.spec.ts` that sets a mobile viewport, loads the app shell, verifies the active channel/chat pane remains reachable, and verifies channel/member navigation has a mobile control.

Run:

```powershell
npm run e2e -w apps/web -- apps/web/tests/e2e/pwa-mobile.spec.ts
```

Expected before implementation: FAIL because PWA/mobile controls are incomplete.

- [ ] **Step 2: Add PWA manifest**

Create `apps/web/public/manifest.webmanifest` with:

```json
{
  "name": "Discord Clone",
  "short_name": "DClone",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#111827",
  "theme_color": "#111827",
  "icons": []
}
```

- [ ] **Step 3: Wire manifest in Nuxt app head**

Add manifest and theme color links in `apps/web/nuxt.config.ts` app head.

- [ ] **Step 4: Implement mobile shell controls**

Use CSS breakpoints and Vue state so mobile shows one primary pane at a time with reachable channel and member navigation. Keep desktop multi-pane behavior unchanged.

- [ ] **Step 5: Verify**

Run:

```powershell
npm run test -w apps/web
npm run e2e -w apps/web
npm run build -w apps/web
```

Expected: component tests, Playwright tests, and Nuxt build pass.

- [ ] **Step 6: Commit**

```powershell
git add apps/web
git commit -m "feat: add pwa mobile shell baseline"
```

## Task 3: Tauri Desktop Shell

**Files:**
- Modify: `package.json`
- Create: `apps/desktop/package.json`
- Create: `apps/desktop/src-tauri/tauri.conf.json`
- Create: `apps/desktop/src/capabilities.ts`
- Create: `apps/desktop/tests/desktop-shell.contract.test.ts`

- [ ] **Step 1: Add desktop workspace**

Change root workspaces to include:

```json
"apps/desktop"
```

- [ ] **Step 2: Add desktop shell package**

Create `apps/desktop/package.json` with scripts for `dev`, `build`, and `test`. The `dev` script should start Tauri against the Nuxt dev server, and `build` should package against the Nuxt production build.

- [ ] **Step 3: Add minimal Tauri config**

Create `apps/desktop/src-tauri/tauri.conf.json` with the app identifier, window title, and minimal permissions. Do not enable filesystem or shell permissions unless a task explicitly proves the need.

- [ ] **Step 4: Add capability boundary test**

Create a test proving desktop notification, tray, and deep link calls go through `apps/desktop/src/capabilities.ts` and not through direct imports inside `apps/web`.

- [ ] **Step 5: Verify**

Run:

```powershell
npm run test -w apps/desktop
npm run build -w apps/web
```

Expected: desktop contract test and web build pass. Tauri packaging may require local Rust/Tauri prerequisites and should be recorded separately if unavailable.

- [ ] **Step 6: Commit**

```powershell
git add package.json apps/desktop
git commit -m "feat: add tauri desktop shell baseline"
```

## Task 4: Native Mobile Decision And Expo Spike

**Files:**
- Create: `docs/02-design/features/T30-native-mobile-decision.design.md`
- Modify: `package.json` only if the decision keeps an Expo workspace
- Create: `apps/mobile/package.json` only if native spike is accepted
- Create: `apps/mobile/app/(auth)/login.tsx`
- Create: `apps/mobile/app/(main)/channels.tsx`
- Create: `apps/mobile/tests/navigation.contract.test.tsx`

- [ ] **Step 1: Write decision record**

Create `docs/02-design/features/T30-native-mobile-decision.design.md` with a table comparing PWA-only, PWA-first/native-later, and native-parallel. Use product requirements as criteria: push, background session, media, app store distribution, native share, file picker, and QA cost.

- [ ] **Step 2: Choose track**

Default decision is PWA-first/native-later unless two or more native-only capabilities are required for the next release.

- [ ] **Step 3: If native spike is kept, add Expo workspace**

Add `apps/mobile` to root workspaces and create a minimal Expo app shell that consumes shared screen contract names and design token names.

- [ ] **Step 4: Add navigation contract test**

Test that native mobile exposes login, guild/channel list, and message read screen routes matching the PWA mobile IA.

- [ ] **Step 5: Verify**

Run:

```powershell
npm run test --workspaces
npm run build -w apps/web
```

Expected: shared, web, and any accepted mobile workspace tests pass.

- [ ] **Step 6: Commit**

```powershell
git add docs/02-design/features/T30-native-mobile-decision.design.md package.json apps/mobile
git commit -m "docs: record native mobile frontend decision"
```

## Self-Review Checklist

- T27 defines contracts before implementation.
- T28 implements PWA/mobile web without forking API behavior.
- T29 adds desktop shell behind adapter boundaries.
- T30 prevents native mobile work from starting without a documented decision.
- Every implementation task has a test command and commit point.
