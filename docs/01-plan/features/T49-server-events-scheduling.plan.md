# T49 Server Events & Scheduling Plan

Date: 2026-05-18
Slice: T49 Server Events & Scheduling

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T49 adds the first server event scheduling domain. The first slice prioritizes state transitions and channel visibility over UI.

## Implementation Plan

- Add `backend/modules/event`.
- Create scheduled server event associated with a voice/stage channel.
- RSVP members as interested.
- Cancel events and emit audit/notification candidates.
- List only events whose channel id is in the caller-visible channel set.

## Acceptance Criteria

- Authorized creator can create event.
- Member can RSVP once.
- Hidden channel event is not listed for callers without channel visibility.
- Cancelled event records candidate audit/notification event.
