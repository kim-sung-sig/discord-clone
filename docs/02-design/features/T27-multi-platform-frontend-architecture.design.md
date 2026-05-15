# T27 Multi-Platform Frontend Architecture Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T27 Multi-Platform Frontend Architecture & Screen Contracts

## 1. Architecture Decision

선택: PWA-first + Tauri desktop shell + Expo native candidate.

이유:

- Nuxt 웹 shell은 이미 구현과 QA 하네스가 존재하므로 가장 빠른 기준선이다.
- PWA는 모바일 설치성과 responsive UX를 가장 낮은 비용으로 검증한다.
- Tauri 2는 Electron 대비 기본 메모리/번들 비용이 낮고, Discord-like desktop shell에 필요한 tray, notification, deep link, window state를 최소 권한 allowlist로 단계 확장하기 좋다.
- Expo React Native는 PWA로 해결하기 어려운 push, background session, native media/file picker, app store 배포 요구가 확정될 때만 병렬 트랙으로 승격한다.

## 2. Platform Surface Matrix

| Surface | Primary Tech | Layout Rule | Native Capability | QA Gate |
| --- | --- | --- | --- | --- |
| Web desktop | Nuxt 3/4 + Vue + Pinia | server rail + channel sidebar + chat + member sidebar | Browser notification only | Vitest, Storybook, Playwright desktop |
| Mobile PWA | Nuxt PWA | single active pane + drawer/bottom navigation | manifest, service worker, web push 후보 | Playwright mobile viewport, PWA manifest/SW smoke |
| Desktop app | Tauri 2 + Nuxt build/webview | wide multi-pane + OS shell adapters | tray, notification, deep link, window state | Tauri boot smoke, capability allowlist review |
| Native mobile candidate | Expo React Native | stack/tab navigation matching PWA IA | push, background, media, file picker, native share | component/navigation tests, decision record |

## 3. Shared Package Boundaries

```text
packages/api-client/
  auth.ts
  guild.ts
  channel.ts
  message.ts
  gateway.ts
  errors.ts

packages/design-tokens/
  color.ts
  typography.ts
  spacing.ts
  motion.ts
  platform.ts

packages/ui-contracts/
  screens.ts
  navigation.ts
  permissions.ts
  presence.ts
  unread.ts
  errors.ts

packages/platform-shell/
  capabilities.ts
  pwa.ts
  desktop.ts
  mobile.ts
```

Rules:

- API error shape, auth state, request id propagation은 `api-client`가 소유한다.
- color/spacing/type/motion token은 web/mobile/desktop adapter에서 변환하되 의미 토큰 이름은 공유한다.
- route, pane, permission visibility, unread/presence 표현 규칙은 `ui-contracts`가 소유한다.
- notification, deep link, tray, push, background capability는 `platform-shell` interface 뒤로 숨긴다.

## 4. Screen Information Architecture

### Web Desktop

- 좌측 server rail
- channel/category/thread/forum navigation
- 중앙 chat/voice/stage/forum primary viewport
- 우측 member/activity/sidebar
- 하단 user panel and voice status

### Mobile PWA

- top app bar: active guild/channel context
- bottom navigation: Home, Channels, DMs, Voice, Me
- drawers/sheets: server switcher, channel list, member list
- primary pane: chat or voice room one screen at a time
- composer: safe-area and mobile keyboard aware

### Desktop App

- web desktop layout 재사용
- shell-only features: tray reconnect status, OS notification, invite deep link, persisted window bounds
- desktop state must not bypass web auth/permission policy

### Native Mobile Candidate

- PWA mobile IA와 동일한 stack/tab model
- native capability만 adapter로 추가
- initial spike는 read-only guild/channel/message shell과 auth navigation까지로 제한

## 5. Regression Analysis

| Requirement | Design Choice | Regression If Skipped |
| --- | --- | --- |
| 동일 API 동작 | shared `api-client` | 플랫폼별 API 호출 분기와 에러 처리 불일치 |
| 동일 권한 표시 | `ui-contracts/permissions` | 웹은 숨기고 모바일은 노출하는 보안/UX 결함 |
| 모바일 사용성 | PWA single-pane IA | multi-pane 축소로 channel/member/chat 접근성 붕괴 |
| 데스크톱 확장 | Tauri adapter boundary | native API가 Vue component에 직접 섞여 web build 회귀 |
| native 판단 | Expo spike + decision record | 근거 없는 native 병렬 개발로 일정/QA 폭증 |
| QA 반복성 | surface별 smoke gate | 새 플랫폼이 컴파일만 되고 실제 화면 품질은 미검증 |

## 6. Implementation Order

1. T27: shared screen/capability contracts and documentation.
2. T28: Nuxt PWA/mobile responsive shell and mobile viewport QA.
3. T29: Tauri desktop shell with minimal OS capability adapters.
4. T30: Expo native decision spike and minimal shell, then decide whether native remains backlog or becomes product track.

## 7. Test Harness Requirements

- T28: `apps/web` Vitest + Playwright mobile viewport + PWA manifest/SW smoke.
- T29: Tauri boot smoke plus contract test that desktop capabilities are adapter-based.
- T30: Expo component/navigation tests and decision record review.
- All: existing real-backend QA harness must remain the source of truth for API-connected user flows.
