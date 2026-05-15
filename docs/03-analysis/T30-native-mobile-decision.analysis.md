# T30 Native Mobile Decision Analysis

작성일: 2026-05-15  
Slice: T30 Native Mobile App Decision & Expo Shell Spike

## Verification Matrix

| Gate | Evidence | Result |
| --- | --- | --- |
| Decision record exists | `docs/02-design/features/T30-native-mobile-decision.design.md` | PASS |
| Required options compared | PWA-only, PWA-first/native-later, native-parallel | PASS |
| Native-only criteria checked | push, background session, media, app store distribution, native share, file picker, QA cost, schedule risk, shared contract reuse | PASS |
| Decision threshold applied | Native-parallel only if two or more native-only capabilities are required for next release | PASS |
| No Expo scaffold created | `apps/mobile` not created | PASS |

## Decision

Use `PWA-first/native-later`.

No native-only capability is currently required for the next release. T30 therefore remains a decision record and does not create an Expo workspace.

## Residual Risks

- Web push/background behavior may be insufficient later.
- Browser media/file picker behavior may not match native expectations.
- Delaying Expo can compress schedule if native requirements appear late.
- Shared contracts must stay stable so a later native app can reuse them.
