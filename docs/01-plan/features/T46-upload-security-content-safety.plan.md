# T46 Upload Security & Content Safety Plan

Date: 2026-05-18
Slice: T46 Upload Security & Content Safety

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T46 hardens the attachment upload path. Existing storage code already handles content-type allowlists, server-generated object keys, scoped download, and orphan cleanup. The missing safety boundaries are file signature validation and explicit scanner failure behavior.

## Implementation Plan

Major topics:

1. Filename/content-type consistency
   - Reject common image extension and content-type mismatches during upload request.

2. File signature validation
   - Add byte signature validation for PNG and JPEG uploads.
   - Reject uploaded bytes that do not match the declared content type.

3. Malware scanning provider skeleton
   - Add scanner interface and scan result model.
   - Default policy blocks when scanner result is not clean.

## Out of Scope

- Real antivirus provider integration.
- Remote preview fetching.
- Browser-side file picker validation.
- Production object-store callbacks.

## Acceptance Criteria

- `.png` filename with `image/jpeg` content type is rejected.
- `image/png` upload with JPEG bytes is rejected before object store persistence.
- Scanner failure blocks upload completion with a clear error.
- Existing scoped download and orphan cleanup tests remain green.

## Failure Criteria

- Uploaded bytes can be marked uploaded without a clear validation path.
- Scanner failure silently allows the attachment.
- Object keys can be influenced by user filename.
