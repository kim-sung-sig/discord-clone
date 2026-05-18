# T46 Upload Security & Content Safety Design

Date: 2026-05-18
Slice: T46 Upload Security & Content Safety

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Architecture

T46 stays inside `backend/modules/storage`:

- `AttachmentUploadPolicy`: request-time filename/content-type consistency.
- `FileSignatureValidator`: upload-time magic-byte validation.
- `AttachmentScanner`: provider boundary for malware scanning.
- `InMemoryAttachmentService`: validates and scans before marking uploaded.

## Signature Validation

The first supported signatures are:

- `image/png`: `89 50 4E 47 0D 0A 1A 0A`
- `image/jpeg`: `FF D8 FF`

Unsupported but allowed content types can be added later with explicit signatures. Empty or missing bytes are invalid on the secure upload-completion path.

## Scanner Policy

The scanner provider returns `AttachmentScanResult`:

- `clean()`: allow upload completion.
- `blocked(reason)`: reject upload completion.
- `unavailable(reason)`: reject upload completion.

This makes the fail-open/fail-closed behavior explicit. T46 uses fail-closed.

## Compatibility

The legacy `markUploaded(attachmentId, ownerId)` method remains for existing in-memory test paths, but the secure path is `markUploaded(attachmentId, ownerId, bytes)`. Follow-up API work should route production upload completion through the secure method only.

## Testing

TDD tests cover:

- Extension/content-type mismatch rejection.
- Signature/content-type mismatch rejection.
- Scanner unavailable rejection.
- Existing upload/download/orphan behavior remains green.
