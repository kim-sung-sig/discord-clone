# T27 Multi-Platform Frontend Architecture Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T27 Multi-Platform Frontend Architecture & Screen Contracts

## 1. Problem

현재 프론트 계획은 Nuxt 웹 shell 중심이다. Discord 클론을 데스크톱 앱과 모바일 앱까지 확장하려면 화면 정보구조, shared API/design contract, platform capability boundary를 먼저 정의해야 한다. 이를 생략하면 PWA, 데스크톱, 네이티브 모바일이 각각 UI fork가 되어 권한, presence, unread, error handling이 플랫폼마다 달라진다.

## 2. Solution

Nuxt 웹 shell을 기준 UI로 유지하고, 다음 네 표면을 하나의 프론트 제품군으로 설계한다.

- Web desktop: 기존 Nuxt multi-pane shell.
- Mobile PWA: Nuxt 기반 installable/mobile-first shell.
- Desktop app: Tauri 2 shell로 Nuxt app을 감싸는 경량 desktop app.
- Native mobile candidate: Expo React Native shell spike로 native 필요성을 검증.

공통 계약은 `packages/api-client`, `packages/design-tokens`, `packages/ui-contracts`, `packages/platform-shell`로 분리한다.

## 3. Scope

- 플랫폼별 screen IA와 navigation pattern 정의
- PWA/desktop/native capability matrix 작성
- shared package 경계 정의
- T28~T30 구현 태스크의 성공/실패 기준 확정
- QA harness 확장 방향 정의

## 4. Out of Scope

- Tauri/Expo 실제 앱 scaffold 구현
- PWA service worker 구현
- native push/background/media 구현
- 앱스토어 배포, 서명, 자동 업데이트

## 5. Success Criteria

- 웹, PWA mobile, Tauri desktop, Expo 후보의 화면 IA가 문서화된다.
- UI fork 대신 shared contract로 공유할 항목과 platform adapter로 분기할 항목이 구분된다.
- T28, T29, T30이 독립 구현 가능한 수준의 task로 정리된다.
- QA 기준에 mobile viewport, PWA installability, desktop shell smoke, native candidate component/navigation test가 포함된다.

## 6. Failure Criteria

- 데스크톱/모바일 계획이 “Nuxt 화면을 그대로 줄인다” 수준에 머문다.
- PWA와 native mobile 중 무엇을 언제 선택할지 기준이 없다.
- platform-specific API가 Nuxt component 내부에 직접 섞인다.
- shared API/design token contract 없이 플랫폼별 화면이 독립 구현된다.

## 7. QA Strategy

- 문서 리뷰: task breakdown과 enterprise design이 동일한 T27~T30 범위를 가리키는지 확인한다.
- Contract review: shared packages가 API, token, screen, platform capability를 분리하는지 확인한다.
- Future harness plan: Playwright mobile viewport, Lighthouse/PWA manifest check, Tauri smoke, Expo component/navigation test를 T28~T30의 완료 기준으로 둔다.
