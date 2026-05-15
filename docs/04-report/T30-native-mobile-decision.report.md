# T30 Native Mobile Decision Report

작성일: 2026-05-15  
Slice: T30 Native Mobile App Decision & Expo Shell Spike

## Summary

T30 completed the native mobile decision phase. The selected path is `PWA-first/native-later`, so no `apps/mobile` or Expo scaffold is added yet.

## Completed

- Added native mobile decision record.
- Compared PWA-only, PWA-first/native-later, and native-parallel.
- Defined escalation gate: start native-parallel only when two or more native-only capabilities are required for the next release.
- Confirmed current native-only requirement count is zero.

## Verification

- Decision record reviewed against T27/T30 plan.
- No `apps/mobile` workspace was created.
- Existing web and shared workspace tests pass as part of T27 verification.

## Next

- Continue T28 PWA/mobile web shell as the mobile delivery path.
- Revisit native decision after PWA notification, media, share, and attachment QA evidence exists.
