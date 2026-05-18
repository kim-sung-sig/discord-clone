# T47 Accessibility & Responsive UX Pass Plan

Date: 2026-05-18
Slice: T47 Accessibility & Responsive UX Pass

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T47 improves keyboard and accessibility behavior in the existing Nuxt shell. The first slice focuses on high-impact navigation and modal behavior without redesigning the whole interface.

## Implementation Plan

Major topics:

1. Skip path
   - Add a keyboard-visible skip link.
   - Give the workspace a stable focus target.

2. Modal focus containment
   - Keep focus inside the invite modal when Tab/Shift+Tab reaches the edge.
   - Preserve initial focus behavior.

3. Accessible control names
   - Verify primary composer and modal controls expose accessible names.

## Out of Scope

- Full visual regression screenshot matrix.
- Complete responsive redesign.
- Automated contrast tooling.

## Acceptance Criteria

- Keyboard users can skip directly to the workspace.
- Invite modal traps Tab navigation inside the dialog.
- Existing app shell tests remain green.

## Failure Criteria

- Focus escapes the modal to background content.
- Skip link points to a missing target.
- Existing composer/channel behavior regresses.
